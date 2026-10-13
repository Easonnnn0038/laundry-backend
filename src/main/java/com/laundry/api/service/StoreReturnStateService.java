package com.laundry.api.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;

@Service
public class StoreReturnStateService {
    private final JdbcTemplate jdbc;

    public StoreReturnStateService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void start(String storeCode, long orderId, Collection<Long> itemIds, LocalDateTime now) {
        for (Long itemId : itemIds) {
            jdbc.update("DELETE FROM shelf_position WHERE store_code=? AND order_item_id=? AND status='OCCUPIED'", storeCode, itemId);
            int changed = jdbc.update("""
                    UPDATE order_item SET status='STORE_REWORKING',shelf_status=0,shelf_code=NULL,
                        off_shelf_time=?,update_time=? WHERE id=? AND status='BACK_TO_STORE'
                    """, now, now, itemId);
            if (changed != 1) throw new IllegalArgumentException("返洗衣物状态已变化，请刷新后重试");
        }
        int changed = jdbc.update("""
                UPDATE laundry_order SET status='STORE_REWORKING',update_time=?
                WHERE id=? AND status IN ('BACK_TO_STORE','NOTIFIED')
                """, now, orderId);
        if (changed != 1) throw new IllegalArgumentException("来源订单状态已变化，请刷新后重试");
    }
}
