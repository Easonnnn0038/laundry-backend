package com.laundry.api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PickupServiceTest {
    @Test
    void closesWithoutPerItemScanButStillRequiresEveryItemBack() {
        assertDoesNotThrow(() -> PickupService.validateCloseState(2, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> PickupService.validateCloseState(2, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> PickupService.validateCloseState(2, 1, 0));
    }
}
