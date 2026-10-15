package com.laundry.api.controller;

import com.laundry.api.utils.CurrentUserUtil;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerControllerTest {
    @Test
    void missingCustomerInCurrentStoreCannotOpenProfileOrCreateContact() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CurrentUserUtil user = mock(CurrentUserUtil.class);
        when(user.getStoreCode()).thenReturn("001");
        when(jdbc.queryForList(contains("FROM customer c LEFT JOIN member_card"), eq(9L), eq("001")))
                .thenReturn(List.of());
        when(jdbc.queryForObject(contains("FROM customer WHERE id"), eq(Integer.class), eq(9L), eq("001")))
                .thenReturn(0);
        CustomerController controller = new CustomerController();
        ReflectionTestUtils.setField(controller, "jdbc", jdbc);
        ReflectionTestUtils.setField(controller, "currentUserUtil", user);

        assertThrows(IllegalArgumentException.class, () -> controller.detail(9L));
        assertThrows(IllegalArgumentException.class, () -> controller.addContact(9L,
                new CustomerController.ContactRequest("PHONE", "REACHED", "test")));
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any(), any());
    }
}
