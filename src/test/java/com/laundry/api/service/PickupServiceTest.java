package com.laundry.api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PickupServiceTest {
    @Test
    void partialPickupOnlyClosesOrderAfterLastItem() {
        assertEquals("PARTIALLY_PICKED_UP", PickupService.pickupStatus(1));
        assertEquals("PICKED_UP", PickupService.pickupStatus(0));
    }
}
