package com.laundry.api.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laundry.api.entity.LaundryOrder;
import com.laundry.api.mapper.LaundryOrderMapper;
import com.laundry.api.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class OrderListServiceTest {
    @Test
    void listsCurrentStoreOrdersWithBoundedPagination() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), LaundryOrder.class);
        LaundryOrderMapper mapper = mock(LaundryOrderMapper.class);
        OrderServiceImpl service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", mapper);

        LaundryOrder order = new LaundryOrder();
        order.setId(7L);
        order.setOrderNo("00120261012001");
        order.setOrderType("CUSTOMER_RETURN");
        order.setOrderSource("MINIAPP");
        order.setStatus("PICKED_UP");
        order.setTotalReceivable(new BigDecimal("88.00"));
        Page<LaundryOrder> dbPage = new Page<>(1, 100, 1);
        dbPage.setRecords(List.of(order));
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(dbPage);

        var result = service.getOrders("001", "20261012", "PICKED_UP", "MINIAPP",
                null, null, 0, 1000);

        assertThat(result.getCurrent()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(100);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getStatusLabel()).isEqualTo("已取衣");
        assertThat(result.getRecords().get(0).getOrderSource()).isEqualTo("MINIAPP");
        assertThat(result.getRecords().get(0).getOrderType()).isEqualTo("CUSTOMER_RETURN");
        ArgumentCaptor<LambdaQueryWrapper<LaundryOrder>> filter = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(any(Page.class), filter.capture());
        assertThat(filter.getValue().getSqlSegment()).contains("storeCode", "orderSource");
        assertThat(filter.getValue().getParamNameValuePairs().values()).contains("001", "MINIAPP");

        assertThrows(IllegalArgumentException.class,
                () -> service.getOrders(null, null, null, null, null, null, 1, 20));
    }
}
