package com.laundry.api.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DealPackageService {
    private final JdbcTemplate jdbc;
    public DealPackageService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Selection(long id, String code, String name, BigDecimal price, String platform,
                            String voucherCode, String ruleSnapshot) {}

    public List<Map<String, Object>> activePackages() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id,code,name,description,platform,price,original_price AS originalPrice,
                  required_quantity AS requiredQuantity,featured
                FROM deal_package WHERE status='ACTIVE'
                  AND (valid_from IS NULL OR valid_from<=NOW()) AND (valid_to IS NULL OR valid_to>=NOW())
                ORDER BY sort_order,id
                """);
        for (Map<String, Object> row : rows) row.put("categoryGroups", jdbc.queryForList(
                "SELECT category_group FROM deal_package_scope WHERE package_id=? ORDER BY category_group",
                String.class, row.get("id")));
        return rows;
    }

    public Selection validate(String code, String platform, String voucherCode,
                              List<PickupOrderService.ItemRequest> items) {
        if (code == null || code.isBlank()) {
            if ((voucherCode != null && !voucherCode.isBlank()) || (platform != null && !platform.isBlank()))
                throw new IllegalArgumentException("使用券码时必须选择团购套餐");
            return null;
        }
        if (voucherCode == null || !voucherCode.trim().matches("[A-Za-z0-9_-]{4,80}"))
            throw new IllegalArgumentException("请输入正确的团购券码");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id,code,name,price,platform,required_quantity FROM deal_package
                WHERE code=? AND status='ACTIVE' AND (valid_from IS NULL OR valid_from<=NOW())
                  AND (valid_to IS NULL OR valid_to>=NOW()) FOR UPDATE
                """, code.trim());
        if (rows.isEmpty()) throw new IllegalArgumentException("套餐不存在、已下架或不在有效期内");
        Map<String, Object> deal = rows.get(0);
        String expectedPlatform = String.valueOf(deal.get("platform"));
        String actualPlatform = platform == null || platform.isBlank() ? expectedPlatform : platform.trim().toUpperCase();
        if (!expectedPlatform.equals(actualPlatform)) throw new IllegalArgumentException("券码平台与套餐不一致");
        int quantity = items.stream().mapToInt(PickupOrderService.ItemRequest::quantity).sum();
        int required = ((Number) deal.get("required_quantity")).intValue();
        if (quantity != required) throw new IllegalArgumentException("该套餐必须选择 " + required + " 件衣物");
        Set<String> allowed = Set.copyOf(jdbc.queryForList(
                "SELECT category_group FROM deal_package_scope WHERE package_id=?", String.class, deal.get("id")));
        String marks = items.stream().map(i -> "?").collect(Collectors.joining(","));
        List<String> groups = jdbc.queryForList("SELECT category_group FROM clothes_category WHERE id IN (" + marks + ") AND status=1",
                String.class, items.stream().map(PickupOrderService.ItemRequest::categoryId).toArray());
        if (groups.size() != items.stream().map(PickupOrderService.ItemRequest::categoryId).distinct().count()
                || groups.stream().anyMatch(group -> !allowed.contains(group)))
            throw new IllegalArgumentException("所选衣物不符合套餐适用范围");
        String rule = "数量" + required + "件；适用品类" + String.join(",", allowed);
        return new Selection(((Number) deal.get("id")).longValue(), String.valueOf(deal.get("code")),
                String.valueOf(deal.get("name")), (BigDecimal) deal.get("price"), expectedPlatform,
                voucherCode.trim().toUpperCase(), rule);
    }

    public void redeem(Selection deal, long pickupOrderId, String phone) {
        if (deal == null) return;
        try {
            jdbc.update("""
                    INSERT INTO deal_voucher(package_id,platform,voucher_code,status,pickup_order_id,customer_phone,redeemed_time)
                    VALUES(?,?,?,'REDEEMED',?,?,NOW())
                    """, deal.id(), deal.platform(), deal.voucherCode(), pickupOrderId, phone);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("该团购券码已核销，请勿重复使用");
        }
        Long voucherId = jdbc.queryForObject("SELECT id FROM deal_voucher WHERE platform=? AND voucher_code=?",
                Long.class, deal.platform(), deal.voucherCode());
        jdbc.update("INSERT INTO deal_voucher_audit(voucher_id,action,pickup_order_id,operator_type,detail) VALUES(?,'REDEEM',?,'MINIAPP',?)",
                voucherId, pickupOrderId, "预约下单核销");
    }
}
