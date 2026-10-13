package com.laundry.api.controller;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class MaintenanceControllerTest {
 @Test void limitsExportDateRange(){LocalDate day=LocalDate.of(2026,1,1);assertDoesNotThrow(()->MaintenanceController.validateDates(day,day.plusDays(366)));assertThrows(IllegalArgumentException.class,()->MaintenanceController.validateDates(day,day.plusDays(367)));assertThrows(IllegalArgumentException.class,()->MaintenanceController.validateDates(day,day.minusDays(1)));}
}
