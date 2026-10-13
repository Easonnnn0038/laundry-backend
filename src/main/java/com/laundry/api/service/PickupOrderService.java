package com.laundry.api.service;

import com.laundry.api.security.MiniappPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PickupOrderService {
    private static final Set<String> SLOTS = Set.of("09:00-12:00", "13:00-17:00", "18:00-20:00");
    private final JdbcTemplate jdbc;
    private final DealPackageService deals;

    public PickupOrderService(JdbcTemplate jdbc, DealPackageService deals) { this.jdbc = jdbc; this.deals = deals; }

    public record ItemRequest(@NotNull Long categoryId, @NotNull @Min(1) @Max(20) Integer quantity) {}
    public record AddressRequest(
            @NotBlank @Size(max = 50) String contactName,
            @NotBlank @Size(max = 255) String detailAddress,
            boolean isDefault) {}
    public record CreateRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{16,64}$") String requestId,
            @NotBlank @Size(max = 50) String contactName,
            @NotBlank @Size(max = 255) String pickupAddress,
            @NotNull @FutureOrPresent LocalDate appointmentDate,
            @NotBlank String appointmentSlot,
            @Size(max = 500) String remark,
            @NotEmpty @Size(max = 50) List<@Valid ItemRequest> items,
            String dealCode,
            String dealPlatform,
            String voucherCode) {}
    private record MemberPricing(Long cardId, String cardTypeName, BigDecimal discountRate) {}

    public List<Map<String, Object>> catalog(String phone) {
        MemberPricing member = memberPricing(phone);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, category_group AS categoryGroup, category_level2 AS name, price AS originalPrice,
                  unit, remark, price_mode AS priceMode, member_price300 AS memberPrice300,
                  member_price500 AS memberPrice500
                FROM clothes_category WHERE status=1 AND category_group<>'CUSTOM'
                ORDER BY category_group, sort_order, id
                """);
        for (Map<String, Object> row : rows) {
            BigDecimal original = (BigDecimal) row.get("originalPrice");
            BigDecimal price = memberPrice(original, ((Number) row.get("priceMode")).intValue(),
                    (BigDecimal) row.get("memberPrice300"), (BigDecimal) row.get("memberPrice500"), member);
            row.put("price", price); row.put("member", member != null);
            if (member != null) { row.put("cardTypeName", member.cardTypeName()); row.put("discountRate", member.discountRate()); }
        }
        return rows;
    }

    public Map<String, Object> defaults(MiniappPrincipal user) {
        List<Map<String, Object>> previous = jdbc.queryForList("""
                SELECT contact_name AS contactName,pickup_address AS pickupAddress
                FROM pickup_order WHERE openid=? ORDER BY id DESC LIMIT 1
                """, user.openid());
        if (!previous.isEmpty()) return previous.get(0);

        List<Map<String, Object>> customers = jdbc.queryForList("""
                SELECT name AS contactName,address AS pickupAddress
                FROM customer WHERE phone=? ORDER BY id DESC LIMIT 1
                """, user.phone());
        if (!customers.isEmpty()) return customers.get(0);
        return Map.of("contactName", "", "pickupAddress", "");
    }

    public Map<String, Object> memberCard(String phone) {
        requirePhone(phone);
        List<Map<String, Object>> cards = jdbc.queryForList("""
                SELECT mc.id,mc.card_no AS cardNo,mc.card_type_name AS cardTypeName,
                  mc.discount_rate AS discountRate,mc.balance,mc.total_recharge AS totalRecharge,
                  mc.total_consume AS totalConsume,s.store_name AS storeName,s.phone AS storePhone
                FROM member_card mc LEFT JOIN store s ON s.store_code=mc.store_code
                WHERE mc.customer_phone=? AND mc.status=1 ORDER BY mc.id DESC LIMIT 1
                """, phone);
        List<Map<String, Object>> options = jdbc.queryForList("""
                SELECT id,name,amount,discount_rate AS discountRate
                FROM member_card_type WHERE status=1 ORDER BY amount,id
                """);
        if (cards.isEmpty()) return Map.of("active", false, "rechargeOptions", options);
        Map<String, Object> card = new LinkedHashMap<>(cards.get(0));
        card.put("active", true);
        card.put("rechargeOptions", options);
        return card;
    }

    public List<Map<String, Object>> addresses(String phone) {
        requirePhone(phone);
        return jdbc.queryForList("""
                SELECT id,contact_name AS contactName,detail_address AS detailAddress,is_default AS isDefault
                FROM miniapp_address WHERE customer_phone=? ORDER BY is_default DESC,id DESC
                """, phone);
    }

    @Transactional
    public Map<String, Object> createAddress(String phone, AddressRequest request) {
        requirePhone(phone);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM miniapp_address WHERE customer_phone=?", Integer.class, phone);
        if (count != null && count >= 10) throw new IllegalArgumentException("最多保存10个地址");
        boolean makeDefault = request.isDefault() || count == null || count == 0;
        if (makeDefault) clearDefault(phone);
        jdbc.update("""
                INSERT INTO miniapp_address(customer_phone,contact_name,detail_address,is_default) VALUES(?,?,?,?)
                """, phone, request.contactName().trim(), request.detailAddress().trim(), makeDefault);
        Long id = jdbc.queryForObject("SELECT id FROM miniapp_address WHERE customer_phone=? AND contact_name=? AND detail_address=?", Long.class,
                phone, request.contactName().trim(), request.detailAddress().trim());
        return address(phone, id);
    }

    @Transactional
    public Map<String, Object> updateAddress(String phone, long id, AddressRequest request) {
        requirePhone(phone); address(phone, id);
        if (request.isDefault()) clearDefault(phone);
        jdbc.update("""
                UPDATE miniapp_address SET contact_name=?,detail_address=?,is_default=? WHERE id=? AND customer_phone=?
                """, request.contactName().trim(), request.detailAddress().trim(), request.isDefault(), id, phone);
        ensureDefault(phone);
        return address(phone, id);
    }

    @Transactional
    public void deleteAddress(String phone, long id) {
        requirePhone(phone);
        int changed = jdbc.update("DELETE FROM miniapp_address WHERE id=? AND customer_phone=?", id, phone);
        if (changed != 1) throw new IllegalArgumentException("地址不存在");
        ensureDefault(phone);
    }

    @Transactional
    public void setDefaultAddress(String phone, long id) {
        requirePhone(phone); address(phone, id); clearDefault(phone);
        jdbc.update("UPDATE miniapp_address SET is_default=1 WHERE id=? AND customer_phone=?", id, phone);
    }

    @Transactional
    public Map<String, Object> create(MiniappPrincipal user, CreateRequest request) {
        if (user.phone() == null || user.phone().isBlank()) throw new IllegalArgumentException("请先绑定手机号");
        if (!SLOTS.contains(request.appointmentSlot())) throw new IllegalArgumentException("预约时间段不正确");
        List<Map<String, Object>> existing = jdbc.queryForList("SELECT id FROM pickup_order WHERE request_id=? AND openid=?", request.requestId(), user.openid());
        if (!existing.isEmpty()) return detailForUser(((Number) existing.get(0).get("id")).longValue(), user.openid());
        String storeCode = jdbc.queryForObject("SELECT store_code FROM store ORDER BY id LIMIT 1", String.class);
        if (storeCode == null) throw new IllegalStateException("尚未配置营业门店");
        DealPackageService.Selection deal = deals.validate(request.dealCode(), request.dealPlatform(), request.voucherCode(), request.items());

        MemberPricing member = memberPricing(user.phone());
        BigDecimal originalTotal = BigDecimal.ZERO, total = BigDecimal.ZERO;
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (ItemRequest item : request.items()) {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT id, category_level2 AS name, price AS originalPrice, unit, price_mode AS priceMode,
                      member_price300 AS memberPrice300, member_price500 AS memberPrice500 FROM clothes_category
                    WHERE id=? AND status=1 AND category_group<>'CUSTOM'
                    """, item.categoryId());
            if (rows.isEmpty()) throw new IllegalArgumentException("衣物类别不存在或已停用");
            Map<String, Object> category = rows.get(0);
            BigDecimal originalPrice = ((BigDecimal) category.get("originalPrice")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal price = memberPrice(originalPrice, ((Number) category.get("priceMode")).intValue(),
                    (BigDecimal) category.get("memberPrice300"), (BigDecimal) category.get("memberPrice500"), member);
            BigDecimal subtotal = lineTotal(price, item.quantity());
            originalTotal = originalTotal.add(lineTotal(originalPrice, item.quantity())); total = total.add(subtotal);
            Map<String, Object> saved = new LinkedHashMap<>(category);
            saved.put("price", price); saved.put("quantity", item.quantity()); saved.put("subtotal", subtotal);
            items.add(saved);
        }
        if (deal != null) total = deal.price().setScale(2, RoundingMode.HALF_UP);
        if (total.signum() <= 0) throw new IllegalArgumentException("订单金额必须大于0");

        String pickupNo = "PU" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        jdbc.update("""
                INSERT INTO pickup_order(request_id,pickup_no,openid,customer_phone,contact_name,store_code,pickup_address,
                  appointment_date,appointment_slot,remark,status,original_amount,discount_amount,member_card_id,
                  member_card_type,discount_rate,estimated_amount,paid_amount,deal_package_id,deal_code,deal_name,
                  deal_price,deal_platform,deal_voucher_code,deal_rule_snapshot)
                VALUES(?,?,?,?,?,?,?,?,?,?,'SUBMITTED',?,?,?,?,?,?,0,?,?,?,?,?,?,?)
                """, request.requestId(), pickupNo, user.openid(), user.phone(), request.contactName().trim(), storeCode,
                request.pickupAddress().trim(), request.appointmentDate(), request.appointmentSlot(),
                blankToNull(request.remark()), originalTotal, originalTotal.subtract(total), member == null ? null : member.cardId(),
                member == null ? null : member.cardTypeName(), member == null ? null : member.discountRate(), total,
                deal == null ? null : deal.id(), deal == null ? null : deal.code(), deal == null ? null : deal.name(),
                deal == null ? null : deal.price(), deal == null ? null : deal.platform(),
                deal == null ? null : deal.voucherCode(), deal == null ? null : deal.ruleSnapshot());
        Long id = jdbc.queryForObject("SELECT id FROM pickup_order WHERE pickup_no=?", Long.class, pickupNo);
        for (Map<String, Object> item : items) jdbc.update("""
                INSERT INTO pickup_order_item(pickup_order_id,category_id,category_name,unit,quantity,catalog_price,unit_price,subtotal)
                VALUES(?,?,?,?,?,?,?,?)
                """, id, item.get("id"), item.get("name"), item.get("unit"), item.get("quantity"), item.get("originalPrice"), item.get("price"), item.get("subtotal"));
        deals.redeem(deal, id, user.phone());
        saveAddressIfMissing(user.phone(), request.contactName().trim(), request.pickupAddress().trim());
        return detailForUser(id, user.openid());
    }

    public List<Map<String, Object>> listForUser(String openid) {
        return jdbc.queryForList("""
                SELECT id,pickup_no AS pickupNo,status,estimated_amount AS estimatedAmount,paid_amount AS paidAmount,
                  appointment_date AS appointmentDate,appointment_slot AS appointmentSlot,pickup_address AS pickupAddress,
                  create_time AS createTime FROM pickup_order WHERE openid=? ORDER BY id DESC LIMIT 100
                """, openid);
    }

    public Map<String, Object> detailForUser(long id, String openid) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM pickup_order WHERE id=? AND openid=?", id, openid);
        if (rows.isEmpty()) throw new IllegalArgumentException("上门取衣单不存在");
        return withItems(rows.get(0));
    }

    public List<Map<String, Object>> listForStore(String storeCode, String status) {
        if (status == null || status.isBlank()) return jdbc.queryForList("""
                SELECT id,pickup_no AS pickupNo,contact_name AS contactName,customer_phone AS customerPhone,
                  pickup_address AS pickupAddress,appointment_date AS appointmentDate,appointment_slot AS appointmentSlot,
                  status,member_card_type AS memberCardType,discount_amount AS discountAmount,formal_order_no AS formalOrderNo,
                  estimated_amount AS estimatedAmount,paid_amount AS paidAmount,create_time AS createTime
                FROM pickup_order WHERE store_code=? ORDER BY appointment_date,id DESC LIMIT 200
                """, storeCode);
        return jdbc.queryForList("""
                SELECT id,pickup_no AS pickupNo,contact_name AS contactName,customer_phone AS customerPhone,
                  pickup_address AS pickupAddress,appointment_date AS appointmentDate,appointment_slot AS appointmentSlot,
                  status,member_card_type AS memberCardType,discount_amount AS discountAmount,formal_order_no AS formalOrderNo,
                  estimated_amount AS estimatedAmount,paid_amount AS paidAmount,create_time AS createTime
                FROM pickup_order WHERE store_code=? AND status=? ORDER BY appointment_date,id DESC LIMIT 200
                """, storeCode, status.trim().toUpperCase());
    }

    public Map<String, Object> detailForStore(long id, String storeCode) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM pickup_order WHERE id=? AND store_code=?", id, storeCode);
        if (rows.isEmpty()) throw new IllegalArgumentException("上门取衣单不存在");
        return withItems(rows.get(0));
    }

    @Transactional
    public Map<String, Object> advance(long id, String storeCode, String action) {
        int changed;
        if ("confirm".equals(action)) changed = jdbc.update("""
                UPDATE pickup_order SET status='CONFIRMED',update_time=NOW()
                WHERE id=? AND store_code=? AND status IN ('SUBMITTED','PAID')
                """, id, storeCode);
        else if ("picked-up".equals(action)) changed = jdbc.update("""
                UPDATE pickup_order SET status='PICKED_UP',update_time=NOW()
                WHERE id=? AND store_code=? AND status='CONFIRMED'
                """, id, storeCode);
        else throw new IllegalArgumentException("操作不正确");
        if (changed != 1) throw new IllegalArgumentException("订单状态已变化，请刷新后重试");
        return detailForStore(id, storeCode);
    }

    private Map<String, Object> withItems(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>(row);
        result.put("items", jdbc.queryForList("""
                SELECT id,category_id AS categoryId,category_name AS categoryName,unit,quantity,
                  catalog_price AS catalogPrice,unit_price AS unitPrice,subtotal FROM pickup_order_item WHERE pickup_order_id=? ORDER BY id
                """, row.get("id")));
        return result;
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private Map<String, Object> address(String phone, Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id,contact_name AS contactName,detail_address AS detailAddress,is_default AS isDefault
                FROM miniapp_address WHERE id=? AND customer_phone=?
                """, id, phone);
        if (rows.isEmpty()) throw new IllegalArgumentException("地址不存在");
        return rows.get(0);
    }

    private void clearDefault(String phone) { jdbc.update("UPDATE miniapp_address SET is_default=0 WHERE customer_phone=?", phone); }

    private void ensureDefault(String phone) {
        Integer defaults = jdbc.queryForObject("SELECT COUNT(*) FROM miniapp_address WHERE customer_phone=? AND is_default=1", Integer.class, phone);
        if (defaults != null && defaults == 0) jdbc.update("""
                UPDATE miniapp_address SET is_default=1 WHERE customer_phone=? ORDER BY id DESC LIMIT 1
                """, phone);
    }

    private void saveAddressIfMissing(String phone, String contactName, String detailAddress) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM miniapp_address WHERE customer_phone=?", Integer.class, phone);
        jdbc.update("""
                INSERT IGNORE INTO miniapp_address(customer_phone,contact_name,detail_address,is_default) VALUES(?,?,?,?)
                """, phone, contactName, detailAddress, count == null || count == 0);
    }

    private static void requirePhone(String phone) {
        if (phone == null || phone.isBlank()) throw new IllegalArgumentException("请先绑定手机号");
    }

    private MemberPricing memberPricing(String phone) {
        if (phone == null || phone.isBlank()) return null;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id,card_type_name AS cardTypeName,discount_rate AS discountRate
                FROM member_card WHERE customer_phone=? AND status=1 ORDER BY id DESC LIMIT 1
                """, phone);
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        return new MemberPricing(((Number) row.get("id")).longValue(), (String) row.get("cardTypeName"), (BigDecimal) row.get("discountRate"));
    }

    private static BigDecimal memberPrice(BigDecimal original, int priceMode, BigDecimal price300,
                                          BigDecimal price500, MemberPricing member) {
        return calculateMemberPrice(original, priceMode, price300, price500, member == null ? null : member.discountRate());
    }

    static BigDecimal calculateMemberPrice(BigDecimal original, int priceMode, BigDecimal price300,
                                           BigDecimal price500, BigDecimal discountRate) {
        BigDecimal price = original.setScale(2, RoundingMode.HALF_UP);
        if (discountRate == null) return price;
        if (priceMode == 1) return price.multiply(discountRate).divide(BigDecimal.TEN, 2, RoundingMode.HALF_UP);
        if (discountRate.compareTo(new BigDecimal("8.80")) == 0) return price300 == null ? price : price300.setScale(2, RoundingMode.HALF_UP);
        return price500 == null ? price : price500.setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal lineTotal(BigDecimal price, int quantity) {
        if (price == null || price.signum() < 0 || quantity < 1 || quantity > 20) throw new IllegalArgumentException("衣物价格或数量不正确");
        return price.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
    }
}
