package com.laundry.api.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

}
