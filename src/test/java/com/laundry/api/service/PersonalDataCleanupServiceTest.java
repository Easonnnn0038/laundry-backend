package com.laundry.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PersonalDataCleanupServiceTest {
    @Test void unresolvedDebtPreventsEveryDelete() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("miniapp_deletion_request"), eq(7L)))
                .thenReturn(List.of(Map.of("phone", "13800000000", "status", "ACCOUNT_CLOSED")));
        when(jdbc.query(anyString(), any(RowMapper.class), eq("13800000000"))).thenReturn(List.of(11L));
        when(jdbc.queryForObject(contains("debt_amount>0"), eq(Integer.class), eq("13800000000"))).thenReturn(1);
        PersonalDataCleanupService service = new PersonalDataCleanupService(jdbc);
        assertEquals("ON_HOLD", service.clearDue(7L, "管理员").get("status"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void eligibleRowsUseEachOrdersOwnSixMonthDeadline() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("miniapp_deletion_request"), eq(7L)))
                .thenReturn(List.of(Map.of("phone", "13800000000", "status", "ACCOUNT_CLOSED")));
        when(jdbc.query(anyString(), any(RowMapper.class), eq("13800000000"))).thenReturn(List.of(11L));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(0);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString(), anyString())).thenReturn(0);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        PersonalDataCleanupService service = new PersonalDataCleanupService(jdbc);
        assertEquals("COMPLETED", service.clearDue(7L, "管理员").get("status"));
        verify(jdbc).update(argThat(sql -> sql.contains("DATE_ADD(pickup_time,INTERVAL 6 MONTH)")
                && sql.contains("DATE_ADD(cancel_time,INTERVAL 6 MONTH)")), eq("13800000000"));
        verify(jdbc).update(contains("status='COMPLETED'"), eq(7L));
    }
}
