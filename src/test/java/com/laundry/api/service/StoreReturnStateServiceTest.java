package com.laundry.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoreReturnStateServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final StoreReturnStateService service = new StoreReturnStateService(jdbc);

    @Test
    void acceptsOnlySingleRowStateTransitions() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        assertDoesNotThrow(() -> service.start("S001", 10L, List.of(20L), LocalDateTime.now()));

        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1, 0);
        assertThrows(IllegalArgumentException.class,
                () -> service.start("S001", 10L, List.of(20L), LocalDateTime.now()));

        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1, 1, 0);
        assertThrows(IllegalArgumentException.class,
                () -> service.start("S001", 10L, List.of(20L), LocalDateTime.now()));
    }
}
