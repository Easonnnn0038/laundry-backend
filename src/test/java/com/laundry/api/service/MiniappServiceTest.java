package com.laundry.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laundry.api.entity.LaundryOrder;
import com.laundry.api.entity.OrderItem;
import com.laundry.api.mapper.LaundryOrderMapper;
import com.laundry.api.mapper.MiniappUserMapper;
import com.laundry.api.mapper.OrderItemMapper;
import com.laundry.api.mapper.StoreMapper;
import com.laundry.api.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class MiniappServiceTest {
    @Test
    void localSimulationOnlyUpdatesPackedTestOrderWithoutChangingPayment() {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"local-miniapp"});
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), eq(7L), eq("19900000000"))).thenReturn(1);
        MiniappService service = new MiniappService(mock(MiniappUserMapper.class), mock(LaundryOrderMapper.class),
                mock(OrderItemMapper.class), mock(StoreMapper.class), mock(JwtUtil.class),
                new ObjectMapper(), environment, jdbc);
        ReflectionTestUtils.setField(service, "testLoginEnabled", true);
        ReflectionTestUtils.setField(service, "testLoginKey", "local-key");
        ReflectionTestUtils.setField(service, "testPhone", "19900000000");

        assertEquals("DELIVERED", service.testDeliver("19900000000", 7L, "local-key").get("status"));
        verify(jdbc).update(argThat(sql -> sql.contains("order_source='MINIAPP'")
                && sql.contains("fp.status<>'PACKED'") && sql.contains("fis.current_process='RETURN'")
                && !sql.contains("delivery_billing_status='PAID'") && !sql.contains("total_paid=")),
                eq(7L), eq("19900000000"));
        assertThrows(IllegalArgumentException.class, () -> service.testDeliver("19900000000", 8L, "wrong-key"));
        verifyNoMoreInteractions(jdbc);
        LaundryOrder delivered = new LaundryOrder();
        delivered.setStatus("DELIVERED");
        delivered.setOrderSource("MINIAPP");
        delivered.setFulfillmentMethod("HOME_DELIVERY");
        delivered.setCustomerPhone("19900000000");
        List<Map<String, Object>> progress = ReflectionTestUtils.invokeMethod(service, "progress", delivered);
        assertEquals("模拟已签收", progress.get(4).get("label"));
        assertEquals(5, progress.size());
    }

    @Test
    void productionCannotSimulateDeliveryEvenWithMatchingKey() {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "local-miniapp"});
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MiniappService service = new MiniappService(mock(MiniappUserMapper.class), mock(LaundryOrderMapper.class),
                mock(OrderItemMapper.class), mock(StoreMapper.class), mock(JwtUtil.class),
                new ObjectMapper(), environment, jdbc);
        ReflectionTestUtils.setField(service, "testLoginEnabled", true);
        ReflectionTestUtils.setField(service, "testLoginKey", "local-key");
        ReflectionTestUtils.setField(service, "testPhone", "19900000000");

        assertThrows(IllegalArgumentException.class, () -> service.testDeliver("19900000000", 7L, "local-key"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void packedHomeDeliveryIsVisibleButNotMarkedShipped() {
        LaundryOrderMapper orders = mock(LaundryOrderMapper.class);
        OrderItemMapper items = mock(OrderItemMapper.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LaundryOrder order = new LaundryOrder();
        order.setId(7L);
        order.setCustomerPhone("13800000001");
        order.setStatus("SENT_TO_FACTORY");
        order.setFulfillmentMethod("HOME_DELIVERY");
        order.setDeliveryBillingStatus("AWAITING_FEE");
        when(orders.selectList(any())).thenReturn(List.of(order));
        when(orders.selectById(7L)).thenReturn(order);
        OrderItem item = new OrderItem();
        item.setId(17L);
        when(items.selectList(any())).thenReturn(List.of(item));
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(7L))).thenReturn(Map.of(17L, "RETURN"));
        when(jdbc.queryForList(eq("SELECT status FROM factory_package WHERE order_id=?"), eq(String.class), eq(7L)))
                .thenReturn(List.of("PACKED", "PACKED"));
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[0]);
        MiniappService service = new MiniappService(mock(MiniappUserMapper.class), orders, items,
                mock(StoreMapper.class), mock(JwtUtil.class), new ObjectMapper(), environment, jdbc);

        var result = service.orders("13800000001", false).get(0);
        assertEquals("工厂已打包", result.get("statusLabel"));
        assertTrue(result.get("statusHint").toString().contains("尚未交快递"));
        assertEquals("SENT_TO_FACTORY", result.get("status"));
        var detail = service.order("13800000001", 7L);
        assertEquals("已打包", ((List<Map<String, Object>>) detail.get("items")).get(0).get("statusLabel"));
        assertEquals(true, ((List<Map<String, Object>>) detail.get("progress")).get(3).get("current"));
        assertEquals(false, ((List<Map<String, Object>>) detail.get("progress")).get(4).get("done"));
    }

    @Test
    void partiallyPackedHomeDeliveryShowsPartialProgress() {
        LaundryOrderMapper orders = mock(LaundryOrderMapper.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LaundryOrder order = new LaundryOrder();
        order.setId(8L);
        order.setStatus("SENT_TO_FACTORY");
        order.setFulfillmentMethod("HOME_DELIVERY");
        when(orders.selectList(any())).thenReturn(List.of(order));
        when(jdbc.queryForList(eq("SELECT status FROM factory_package WHERE order_id=?"), eq(String.class), eq(8L)))
                .thenReturn(List.of("PACKED", "PROCESSING"));
        MiniappService service = new MiniappService(mock(MiniappUserMapper.class), orders, mock(OrderItemMapper.class),
                mock(StoreMapper.class), mock(JwtUtil.class), new ObjectMapper(), mock(Environment.class), jdbc);

        var result = service.orders("13800000001", false).get(0);
        assertEquals("部分已打包", result.get("statusLabel"));
        assertTrue(result.get("statusHint").toString().contains("1/2"));
    }

    @Test
    void productionCannotEnableTestLoginEvenWithValidKey() {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        MiniappUserMapper users = mock(MiniappUserMapper.class);
        var service = new MiniappService(users, mock(LaundryOrderMapper.class), mock(OrderItemMapper.class),
                mock(StoreMapper.class), mock(JwtUtil.class), new ObjectMapper(), environment, mock(JdbcTemplate.class));
        ReflectionTestUtils.setField(service, "testLoginEnabled", true);
        ReflectionTestUtils.setField(service, "testLoginKey", "correct-key");
        ReflectionTestUtils.setField(service, "testPhone", "13800000001");
        assertThrows(IllegalArgumentException.class, () -> service.testLogin("correct-key"));
        verifyNoInteractions(users);
    }
}
