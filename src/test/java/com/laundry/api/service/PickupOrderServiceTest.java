package com.laundry.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PickupOrderServiceTest {
    @Test
    void calculatesServerSideLineTotal() {
        assertEquals(new BigDecimal("136.50"), PickupOrderService.lineTotal(new BigDecimal("45.50"), 3));
        assertThrows(IllegalArgumentException.class, () -> PickupOrderService.lineTotal(new BigDecimal("10"), 0));
        assertThrows(IllegalArgumentException.class, () -> PickupOrderService.lineTotal(new BigDecimal("-1"), 1));
    }

    @Test
    void appliesTheSameMemberPricingRulesAsStoreReceiving() {
        assertEquals(new BigDecimal("88.00"), PickupOrderService.calculateMemberPrice(
                new BigDecimal("100"), 1, null, null, new BigDecimal("8.80")));
        assertEquals(new BigDecimal("15.00"), PickupOrderService.calculateMemberPrice(
                new BigDecimal("20"), 2, new BigDecimal("15"), new BigDecimal("12"), new BigDecimal("8.80")));
        assertEquals(new BigDecimal("20.00"), PickupOrderService.calculateMemberPrice(
                new BigDecimal("20"), 1, null, null, null));
    }

    @Test
    void bulkConfirmationIsScopedToStoreAndUnexpiredTimeSlot() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(3);
        PickupOrderService service = new PickupOrderService(jdbc, mock(DealPackageService.class));

        assertEquals(3, service.confirmEligible("001"));
        verify(jdbc).update(argThat(sql -> sql.contains("status IN ('SUBMITTED','PAID')")
                && sql.contains("SUBSTRING_INDEX(appointment_slot,'-',-1)>?")
                && sql.contains("AND store_code=?")), any(LocalDate.class), any(LocalDate.class),
                matches("[0-2][0-9]:[0-5][0-9]"), eq("001"));
    }

    @Test
    void dailyJobChecksEveryStore() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new PickupOrderService(jdbc, mock(DealPackageService.class)).autoConfirmEligible();
        verify(jdbc).update(argThat(sql -> !sql.contains("AND store_code=?")),
                any(LocalDate.class), any(LocalDate.class), anyString());
    }

}
