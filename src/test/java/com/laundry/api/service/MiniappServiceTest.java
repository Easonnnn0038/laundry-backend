package com.laundry.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laundry.api.mapper.LaundryOrderMapper;
import com.laundry.api.mapper.MiniappUserMapper;
import com.laundry.api.mapper.OrderItemMapper;
import com.laundry.api.mapper.StoreMapper;
import com.laundry.api.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MiniappServiceTest {
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
