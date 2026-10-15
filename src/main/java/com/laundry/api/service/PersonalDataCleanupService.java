package com.laundry.api.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class PersonalDataCleanupService {
    private final JdbcTemplate jdbc;

    public PersonalDataCleanupService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Only direct service contact data is removed; amounts, vouchers and accounting history remain. */
    @Transactional
    public Map<String, Object> clearDue(long requestId, String operatorName) {
        List<Map<String, Object>> requests = jdbc.queryForList(
                "SELECT phone,status FROM miniapp_deletion_request WHERE id=? FOR UPDATE", requestId);
        if (requests.isEmpty()) throw new IllegalArgumentException("删除申请不存在");
        Map<String, Object> request = requests.get(0);
        if (!"ACCOUNT_CLOSED".equals(request.get("status")))
            throw new IllegalArgumentException("请先核验身份并注销账户，或刷新申请状态");
        String phone = (String) request.get("phone");
        if (phone == null || phone.isBlank()) throw new IllegalArgumentException("申请手机号不存在，无法安全核对订单");

        // Serialise with store order creation, which locks an existing customer row.
        List<Long> customers = jdbc.query("SELECT id FROM customer WHERE phone=? FOR UPDATE",
                (rs, n) -> rs.getLong(1), phone);
        int activeOrders = count("SELECT COUNT(*) FROM laundry_order WHERE customer_phone=? AND status NOT IN ('PICKED_UP','DELIVERED','CANCELLED')", phone);
        int activePickups = count("SELECT COUNT(*) FROM pickup_order WHERE customer_phone=? AND status NOT IN ('CONVERTED','CANCELLED')", phone);
        int debts = count("SELECT COUNT(*) FROM laundry_order WHERE customer_phone=? AND debt_amount>0", phone);
        Integer balanceCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM member_card WHERE balance>0 AND
                  (customer_phone=? OR customer_id IN (SELECT id FROM customer WHERE phone=?))
                """, Integer.class, phone, phone);
        int balances = balanceCount == null ? 0 : balanceCount;
        int otherAccounts = count("SELECT COUNT(*) FROM miniapp_user WHERE phone=?", phone);
        if (activeOrders + activePickups + debts + balances + otherAccounts > 0)
            return Map.of("status", "ON_HOLD", "activeOrders", activeOrders + activePickups,
                    "debts", debts, "memberBalances", balances, "otherAccounts", otherAccounts,
                    "message", "仍有未完结服务、欠款、有余额会员卡或同手机号账户，请先由门店处理");

        int formal = jdbc.update("""
                UPDATE laundry_order SET customer_id=NULL,customer_name=NULL,customer_phone=NULL,
                  customer_address=NULL,delivery_contact_name=NULL,delivery_phone=NULL,
                  delivery_province=NULL,delivery_city=NULL,delivery_district=NULL,delivery_address=NULL,
                  remark=NULL,personal_data_cleared_at=NOW()
                WHERE customer_phone=? AND ((status='PICKED_UP' AND pickup_time IS NOT NULL AND DATE_ADD(pickup_time,INTERVAL 6 MONTH)<=NOW())
                  OR (status='DELIVERED' AND delivered_time IS NOT NULL AND DATE_ADD(delivered_time,INTERVAL 6 MONTH)<=NOW())
                  OR (status='CANCELLED' AND cancel_time IS NOT NULL AND DATE_ADD(cancel_time,INTERVAL 6 MONTH)<=NOW()))
                """, phone);
        int pickup = jdbc.update("""
                UPDATE pickup_order p LEFT JOIN laundry_order o ON o.id=p.formal_order_id
                SET p.customer_phone=NULL,p.contact_name=NULL,p.pickup_address=NULL,
                    p.delivery_contact_name=NULL,p.delivery_phone=NULL,p.delivery_province=NULL,
                    p.delivery_city=NULL,p.delivery_district=NULL,p.delivery_address=NULL,
                    p.remark=NULL,p.personal_data_cleared_at=NOW()
                WHERE p.customer_phone=? AND ((p.status='CANCELLED' AND DATE_ADD(p.update_time,INTERVAL 6 MONTH)<=NOW())
                  OR (p.status='CONVERTED' AND o.personal_data_cleared_at IS NOT NULL))
                """, phone);
        // These contact copies are not needed to retain the transaction or delivery outcome.
        jdbc.update("""
                UPDATE customer_notification n JOIN laundry_order o ON o.id=n.order_id
                SET n.recipient=NULL WHERE n.recipient=? AND o.personal_data_cleared_at IS NOT NULL
                """, phone);
        jdbc.update("""
                UPDATE sms_log s JOIN laundry_order o ON o.id=s.order_id
                SET s.phone=NULL WHERE s.phone=? AND o.personal_data_cleared_at IS NOT NULL
                """, phone);
        jdbc.update("""
                UPDATE deal_voucher v JOIN pickup_order p ON p.id=v.pickup_order_id
                SET v.customer_phone=NULL WHERE v.customer_phone=? AND p.personal_data_cleared_at IS NOT NULL
                """, phone);

        int remaining = count("SELECT COUNT(*) FROM laundry_order WHERE customer_phone=?", phone)
                + count("SELECT COUNT(*) FROM pickup_order WHERE customer_phone=?", phone);
        if (remaining == 0) {
            for (Long customerId : customers) {
                jdbc.update("UPDATE customer_contact_log SET note=NULL WHERE customer_id=?", customerId);
                jdbc.update("UPDATE member_card SET customer_name=NULL,customer_phone=NULL WHERE customer_id=? AND balance=0", customerId);
                jdbc.update("UPDATE customer SET name=NULL,phone=NULL,address=NULL,remark=NULL WHERE id=? AND phone=?", customerId, phone);
            }
            jdbc.update("DELETE FROM miniapp_address WHERE customer_phone=?", phone);
            jdbc.update("UPDATE sms_log SET phone=NULL WHERE phone=? AND order_id IS NULL", phone);
            int changed = jdbc.update("""
                    UPDATE miniapp_deletion_request SET status='COMPLETED',phone=NULL,openid=NULL,resolved_time=NOW()
                    WHERE id=? AND status='ACCOUNT_CLOSED'
                    """, requestId);
            if (changed != 1) throw new IllegalArgumentException("申请状态已变化，请刷新后重试");
        }
        String status = remaining == 0 ? "COMPLETED" : "WAITING_RETENTION";
        if (formal + pickup > 0 || remaining == 0)
            jdbc.update("""
                    INSERT INTO miniapp_deletion_cleanup_audit(request_id,operator_name,formal_orders,pickup_orders,result_status)
                    VALUES(?,?,?,?,?)
                    """, requestId, operatorName, formal, pickup, status);
        return Map.of("status", status,
                "formalOrders", formal, "pickupOrders", pickup, "remainingOrders", remaining,
                "message", remaining == 0 ? "到期服务联系方式已清理；交易和财务记录仍按规定保留" : "已清理到期订单；未满半年的订单保留至各自复核日");
    }

    private int count(String sql, String phone) {
        Integer value = jdbc.queryForObject(sql, Integer.class, phone);
        return value == null ? 0 : value;
    }
}
