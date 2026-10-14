package com.laundry.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PickupOrderServiceTest {
    @Test
    void bulkConfirmationOnlyTargetsEligibleStoreAppointments() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new PickupOrderService(jdbc).confirmEligible("001");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(), any(), any(), eq("001"));
        assertTrue(sql.getValue().contains("status IN ('SUBMITTED','PAID')"));
        assertTrue(sql.getValue().contains("appointment_date>?"));
        assertTrue(sql.getValue().contains("SUBSTRING_INDEX(appointment_slot,'-',-1)>?"));
        assertTrue(sql.getValue().contains("store_code=?"));
    }

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

}
