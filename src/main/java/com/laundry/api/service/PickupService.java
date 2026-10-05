package com.laundry.api.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PickupService {
    private final JdbcTemplate jdbc;
    private final PickupCodeService codeService;
    private final ConcurrentHashMap<String, LookupWindow> lookupWindows = new ConcurrentHashMap<>();

    public PickupService(JdbcTemplate jdbc, PickupCodeService codeService) {
        this.jdbc = jdbc; this.codeService = codeService;
    }

    public int prepareLegacy(String storeCode) { return codeService.prepareLegacy(storeCode); }

    public List<Map<String, Object>> ready(String storeCode) {
        return jdbc.queryForList("""
                SELECT o.order_no AS orderNo,
                       CONCAT(LEFT(o.customer_phone,3),'****',RIGHT(o.customer_phone,4)) AS maskedPhone,
                       SUM(CASE WHEN i.status='BACK_TO_STORE' THEN 1 ELSE 0 END) AS remainingCount,
                       o.debt_amount AS debtAmount, o.status, o.order_source AS orderSource
                FROM laundry_order o JOIN order_item i ON i.order_id=o.id
                WHERE o.store_code=? AND o.pickup_code IS NOT NULL
                  AND o.status IN ('BACK_TO_STORE','NOTIFIED','PARTIALLY_PICKED_UP') AND o.cancel_flag=0
                GROUP BY o.id,o.order_no,o.customer_phone,o.debt_amount,o.status,o.order_source,o.update_time
                ORDER BY o.update_time DESC,o.id DESC LIMIT 100
                """, storeCode);
    }

    public Map<String, Object> lookup(String identifier, String storeCode) {
        limitLookup(storeCode);
        List<Map<String, Object>> orders = findOrders(identifier, storeCode, false);
        for (Map<String, Object> order : orders) assertCanPickup(order);
        return detail(orders);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> close(String identifier, List<Long> itemIds, String storeCode,
                                     Long operatorId, String operatorName) {
        List<Map<String, Object>> orders = findOrders(identifier, storeCode, true);
        for (Map<String, Object> order : orders) assertCanPickup(order);
        LinkedHashSet<Long> selected = new LinkedHashSet<>(itemIds == null ? List.of() : itemIds);
        if (selected.isEmpty() || selected.size() != itemIds.size())
            throw new IllegalArgumentException("请选择需要取走的衣物");

        Map<Long, Map<String, Object>> orderById = new LinkedHashMap<>();
        for (Map<String, Object> order : orders) orderById.put(number(order.get("id")), order);
        String marks = String.join(",", Collections.nCopies(selected.size(), "?"));
        List<Object> args = new ArrayList<>(selected); args.add(storeCode);
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT i.id,i.order_id AS orderId,i.order_no AS orderNo,i.barcode,i.category_name AS categoryName,
                       i.shelf_code AS shelfCode,i.status,i.shelf_status AS shelfStatus
                FROM order_item i JOIN laundry_order o ON o.id=i.order_id
                WHERE i.id IN (%s) AND o.store_code=? FOR UPDATE
                """.formatted(marks), args.toArray());
        if (items.size() != selected.size()) throw new IllegalArgumentException("所选衣物不存在，请刷新后重试");
        for (Map<String, Object> item : items) {
            if (!orderById.containsKey(number(item.get("orderId")))
                    || !"BACK_TO_STORE".equals(item.get("status"))
                    || ((Number) item.get("shelfStatus")).intValue() != 1)
                throw new IllegalArgumentException("所选衣物已被处理或当前不可取，请刷新后重试");
        }

        LocalDateTime now = LocalDateTime.now();
        Set<Long> affectedOrders = new LinkedHashSet<>();
        List<String> shelfCodes = new ArrayList<>();
        for (Map<String, Object> item : items) {
            long itemId = number(item.get("id")); long orderId = number(item.get("orderId"));
            affectedOrders.add(orderId); shelfCodes.add(String.valueOf(item.get("shelfCode")));
            jdbc.update("""
                    INSERT INTO shelf_operation_log(store_code,order_id,order_item_id,order_no,barcode,action,
                        from_shelf_no,operator_id,operator_name,operate_time)
                    VALUES (?,?,?,?,?,'OFF_SHELF',CAST(? AS UNSIGNED),?,?,?)
                    """, storeCode,orderId,itemId,item.get("orderNo"),item.get("barcode"),item.get("shelfCode"),operatorId,operatorName,now);
            jdbc.update("DELETE FROM shelf_position WHERE store_code=? AND order_item_id=? AND status='OCCUPIED'",storeCode,itemId);
            int changed=jdbc.update("""
                    UPDATE order_item SET status='PICKED_UP',shelf_status=2,off_shelf_time=?,update_time=?
                    WHERE id=? AND status='BACK_TO_STORE' AND shelf_status=1
                    """,now,now,itemId);
            if(changed!=1)throw new IllegalArgumentException("衣物状态已变化，请刷新后重试");
        }

        List<Map<String,Object>> results=new ArrayList<>();
        for(Long orderId:affectedOrders){
            Map<String,Object> order=orderById.get(orderId);
            Integer remaining=jdbc.queryForObject("SELECT COUNT(*) FROM order_item WHERE order_id=? AND status='BACK_TO_STORE'",Integer.class,orderId);
            String after=pickupStatus(remaining==null?0:remaining);
            jdbc.update("""
                    UPDATE laundry_order SET status=?,pickup_time=?,pickup_operator=?,update_time=?
                    WHERE id=? AND status IN ('BACK_TO_STORE','NOTIFIED','PARTIALLY_PICKED_UP')
                    """,after,"PICKED_UP".equals(after)?now:null,operatorName,now,orderId);
            long picked=items.stream().filter(i->number(i.get("orderId"))==orderId).count();
            jdbc.update("""
                    INSERT INTO order_operate_log(order_id,order_no,operate_type,operate_desc,before_status,after_status,
                        operator_id,operator_name,operate_time,create_time)
                    VALUES (?,?,'PICKUP',?,?,?,?,?,?,?)
                    """,orderId,order.get("order_no"),"本次取走"+picked+"件，剩余"+remaining+"件",order.get("status"),after,operatorId,operatorName,now,now);
            results.add(Map.of("orderNo",order.get("order_no"),"status",after,"remainingCount",remaining));
        }
        return Map.of("pickedCount",items.size(),"remainingCount",results.stream().mapToInt(r->((Number)r.get("remainingCount")).intValue()).sum(),
                "pickupTime",now,"shelfCodes",shelfCodes,"orders",results);
    }

    private List<Map<String, Object>> findOrders(String identifier, String storeCode, boolean lock) {
        String value=identifier==null?"":identifier.trim();
        boolean phone=value.matches("1\\d{10}"),code=value.matches("\\d{4}");
        if(!phone&&!code)throw new IllegalArgumentException("请输入完整手机号或四位取衣码");
        List<Map<String,Object>> rows=jdbc.queryForList("""
                SELECT id,order_no,customer_name,customer_phone,pickup_code,total_count,debt_amount,status,cancel_flag,order_source
                FROM laundry_order WHERE store_code=? AND %s=?
                  AND status IN ('BACK_TO_STORE','NOTIFIED','PARTIALLY_PICKED_UP') AND cancel_flag=0
                ORDER BY id
                """.formatted(phone?"customer_phone":"pickup_code")+(lock?" FOR UPDATE":""),storeCode,value);
        if(rows.isEmpty())throw new IllegalArgumentException("未找到可取衣订单，请确认手机号或取衣码");
        if(code&&rows.size()>1)throw new IllegalArgumentException("该取衣码对应多个旧订单，请改用完整手机号");
        return rows;
    }

    private void assertCanPickup(Map<String, Object> order) {
        if (((BigDecimal) order.get("debt_amount")).compareTo(BigDecimal.ZERO) > 0)
            throw new IllegalArgumentException("订单 "+order.get("order_no")+" 仍有欠款，请先结清");
        long orderId=number(order.get("id"));
        Integer packageCount=jdbc.queryForObject("SELECT COUNT(*) FROM factory_package WHERE order_id=?",Integer.class,orderId);
        Integer pending=jdbc.queryForObject("SELECT COUNT(*) FROM factory_package WHERE order_id=? AND status<>'BACK_TO_STORE'",Integer.class,orderId);
        if(packageCount==null||packageCount<1||pending==null||pending>0)
            throw new IllegalArgumentException("订单 "+order.get("order_no")+" 尚未全部回店");
    }

    private Map<String, Object> detail(List<Map<String, Object>> orders) {
        List<Map<String,Object>> resultOrders=new ArrayList<>(); int count=0;
        for(Map<String,Object> order:orders){
            List<Map<String,Object>> items=jdbc.queryForList("""
                    SELECT id,barcode,category_name AS categoryName,color,shelf_code AS shelfCode
                    FROM order_item WHERE order_id=? AND status='BACK_TO_STORE' AND shelf_status=1 ORDER BY item_seq
                    """,order.get("id"));
            if(items.isEmpty())continue;
            count+=items.size(); Map<String,Object> one=new LinkedHashMap<>();
            one.put("orderNo",order.get("order_no"));one.put("maskedPhone",mask(String.valueOf(order.get("customer_phone"))));
            one.put("orderSource",order.get("order_source"));
            one.put("items",items);resultOrders.add(one);
        }
        if(resultOrders.isEmpty())throw new IllegalArgumentException("没有可取走的衣物");
        return Map.of("orders",resultOrders,"itemCount",count);
    }

    private static long number(Object value){return ((Number)value).longValue();}
    private static String mask(String phone){return phone.replaceFirst("^(\\d{3})\\d{4}(\\d{4})$","$1****$2");}
    static String pickupStatus(int remaining){return remaining==0?"PICKED_UP":"PARTIALLY_PICKED_UP";}
    private void limitLookup(String storeCode){
        Instant now=Instant.now();
        // ponytail: 单实例每门店限流；改为多实例部署时迁移到网关或 Redis。
        LookupWindow window=lookupWindows.compute(storeCode,(key,old)->old==null||now.isAfter(old.started().plusSeconds(60))
                ?new LookupWindow(now,1):new LookupWindow(old.started(),old.count()+1));
        if(window.count()>60)throw new IllegalArgumentException("查询过于频繁，请稍后再试");
    }
    private record LookupWindow(Instant started,int count){}
}
