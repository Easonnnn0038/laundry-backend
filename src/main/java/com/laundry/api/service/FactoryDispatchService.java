package com.laundry.api.service;

import com.laundry.api.dto.request.CreateFactoryBatchRequest;
import com.laundry.api.mapper.SeqCounterMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class FactoryDispatchService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JdbcTemplate jdbcTemplate;
    private final SeqCounterMapper seqCounterMapper;

    public FactoryDispatchService(JdbcTemplate jdbcTemplate, SeqCounterMapper seqCounterMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.seqCounterMapper = seqCounterMapper;
    }

    public List<Map<String, Object>> eligibleOrders(String storeCode) {
        return jdbcTemplate.queryForList("""
                SELECT id, order_no AS orderNo, customer_name AS customerName,
                       customer_phone AS customerPhone, total_count AS totalCount,
                       urgent_flag AS urgentFlag, receive_time AS receiveTime,
                       remark
                FROM laundry_order
                WHERE store_code = ? AND status = 'RECEIVED' AND cancel_flag = 0
                ORDER BY urgent_flag DESC, receive_time ASC
                """, storeCode);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createBatch(CreateFactoryBatchRequest request, String storeCode,
                                           Long operatorId, String operatorName) {
        List<Long> orderIds = new ArrayList<>(new LinkedHashSet<>(request.getOrderIds()));
        if (orderIds.isEmpty()) throw new IllegalArgumentException("请选择至少一个订单");

        String placeholders = String.join(",", orderIds.stream().map(id -> "?").toList());
        List<Map<String, Object>> orders = jdbcTemplate.queryForList(
                "SELECT * FROM laundry_order WHERE id IN (" + placeholders + ") FOR UPDATE",
                orderIds.toArray());
        if (orders.size() != orderIds.size()) throw new IllegalArgumentException("部分订单不存在，请刷新后重试");

        for (Map<String, Object> order : orders) {
            if (!storeCode.equals(String.valueOf(order.get("store_code")))) {
                throw new IllegalArgumentException("不能发送其他门店的订单");
            }
            if (!"RECEIVED".equals(String.valueOf(order.get("status")))) {
                throw new IllegalArgumentException("订单 " + order.get("order_no") + " 已不在待送厂状态");
            }
        }
        if (orders.size() == 1 && ((Number) orders.get(0).get("urgent_flag")).intValue() != 1) {
            throw new IllegalArgumentException("普通订单不能单独送厂，请至少选择两个订单；加急订单可单独送厂");
        }

        LocalDateTime now = LocalDateTime.now();
        String today = LocalDate.now().format(DATE);
        seqCounterMapper.incrementSeq("FACTORY_BATCH:" + storeCode + ":" + today);
        int batchSeq = seqCounterMapper.getSeq("FACTORY_BATCH:" + storeCode + ":" + today);
        String batchNo = "PC" + storeCode + today + String.format("%03d", batchSeq);
        int itemCount = orders.stream().mapToInt(o -> ((Number) o.get("total_count")).intValue()).sum();

        KeyHolder batchKey = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO factory_batch(batch_no, store_code, order_count, item_count,
                        send_operator, send_time, status, remark, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, batchNo); ps.setString(2, storeCode); ps.setInt(3, orders.size());
            ps.setInt(4, itemCount); ps.setString(5, operatorName); ps.setObject(6, now);
            ps.setString(7, request.getRemark()); ps.setObject(8, now); ps.setObject(9, now);
            return ps;
        }, batchKey);
        long batchId = batchKey.getKey().longValue();

        List<Map<String, Object>> packages = new ArrayList<>();
        int packageIndex = 0;
        for (Map<String, Object> order : orders) {
            packageIndex++;
            long orderId = ((Number) order.get("id")).longValue();
            String orderNo = String.valueOf(order.get("order_no"));
            int totalCount = ((Number) order.get("total_count")).intValue();

            // TODO(scanner/package-split): 超过5件时后续由门店选择具体衣物进行拆包；当前默认整单一个大件。
            String packageNo = "PK" + storeCode + today + String.format("%03d%03d", batchSeq, packageIndex);
            KeyHolder packageKey = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO factory_package(package_no, order_id, order_no, source_batch_id,
                            source_batch_no, package_seq, expected_item_count, received_item_count,
                            status, create_time, update_time)
                        VALUES (?, ?, ?, ?, ?, 1, ?, 0, 'WAIT_FACTORY', ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, packageNo); ps.setLong(2, orderId); ps.setString(3, orderNo);
                ps.setLong(4, batchId); ps.setString(5, batchNo); ps.setInt(6, totalCount);
                ps.setObject(7, now); ps.setObject(8, now);
                return ps;
            }, packageKey);
            long packageId = packageKey.getKey().longValue();

            List<Map<String, Object>> items = jdbcTemplate.queryForList(
                    "SELECT id, barcode FROM order_item WHERE order_id = ? ORDER BY item_seq", orderId);
            if (items.size() != totalCount) {
                throw new IllegalArgumentException("订单 " + orderNo + " 的衣物数量与明细不一致");
            }
            for (Map<String, Object> item : items) {
                jdbcTemplate.update("""
                        INSERT INTO factory_package_item(package_id, package_no, order_item_id, barcode, scan_status)
                        VALUES (?, ?, ?, ?, 'WAIT_SCAN')
                        """, packageId, packageNo, item.get("id"), item.get("barcode"));
            }
            jdbcTemplate.update("""
                    UPDATE order_item SET batch_id = ?, batch_no = ?, send_factory_time = ?,
                        status = 'SENT_TO_FACTORY', update_time = ? WHERE order_id = ?
                    """, batchId, batchNo, now, now, orderId);
            jdbcTemplate.update("UPDATE laundry_order SET status='SENT_TO_FACTORY', update_time=? WHERE id=?", now, orderId);
            jdbcTemplate.update("""
                    INSERT INTO order_operate_log(order_id, order_no, operate_type, operate_desc,
                        before_status, after_status, operator_id, operator_name, operate_time, remark, create_time)
                    VALUES (?, ?, 'SEND', ?, 'RECEIVED', 'SENT_TO_FACTORY', ?, ?, ?, ?, ?)
                    """, orderId, orderNo, "订单已打包送厂，批次号 " + batchNo + "，大件码 " + packageNo,
                    operatorId, operatorName, now, request.getRemark(), now);

            Map<String, Object> packageInfo = new LinkedHashMap<>();
            packageInfo.put("packageNo", packageNo); packageInfo.put("orderNo", orderNo);
            packageInfo.put("itemCount", totalCount); packages.add(packageInfo);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", batchId); result.put("batchNo", batchNo);
        result.put("orderCount", orders.size()); result.put("itemCount", itemCount);
        result.put("packages", packages);
        return result;
    }
}

