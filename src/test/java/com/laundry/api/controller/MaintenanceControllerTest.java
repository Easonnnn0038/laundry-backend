package com.laundry.api.controller;
import com.laundry.api.entity.User;
import com.laundry.api.service.MaintenanceLogService;
import com.laundry.api.service.MiniappService;
import com.laundry.api.utils.CurrentUserUtil;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class MaintenanceControllerTest {
 @Test void limitsExportDateRange(){LocalDate day=LocalDate.of(2026,1,1);assertDoesNotThrow(()->MaintenanceController.validateDates(day,day.plusDays(366)));assertThrows(IllegalArgumentException.class,()->MaintenanceController.validateDates(day,day.plusDays(367)));assertThrows(IllegalArgumentException.class,()->MaintenanceController.validateDates(day,day.minusDays(1)));}

 @Test void unfinishedServiceDoesNotMarkDeletionRequestHandled(){
   JdbcTemplate jdbc=mock(JdbcTemplate.class);CurrentUserUtil operator=mock(CurrentUserUtil.class);
   MiniappService miniapp=mock(MiniappService.class);User admin=new User();admin.setRole("ADMIN");
   when(operator.getUser()).thenReturn(admin);
   when(jdbc.query(anyString(),any(RowMapper.class),eq(7L))).thenReturn(List.of("openid-1"));
   when(jdbc.queryForList(anyString(),eq("openid-1"))).thenReturn(List.of(Map.of("id",1L)));
   when(jdbc.queryForList(anyString(),eq(7L))).thenReturn(List.of(Map.of("status","PENDING")));
   when(miniapp.closeAccount("openid-1")).thenReturn(Map.of("status","PENDING_SERVICE","message","服务尚未完成"));
   var controller=new MaintenanceController(jdbc,operator,mock(MaintenanceLogService.class),miniapp,mock(com.laundry.api.service.PersonalDataCleanupService.class));
   var result=controller.closeMiniappAccount(7L,new MaintenanceController.DeletionReviewRequest(true,"已现场核验"));
   assertEquals("PENDING_SERVICE",result.getData().get("status"));
   verify(jdbc,never()).update(anyString(),any(),any(),any());
 }

 @Test void verifiedAdministratorCanRecordAccountClosure(){
   JdbcTemplate jdbc=mock(JdbcTemplate.class);CurrentUserUtil operator=mock(CurrentUserUtil.class);
   User admin=new User();admin.setRole("ADMIN");when(operator.getUser()).thenReturn(admin);
   when(operator.getOperatorName()).thenReturn("管理员");
   when(jdbc.query(anyString(),any(RowMapper.class),eq(7L))).thenReturn(List.of("openid-1"));
   when(jdbc.queryForList(anyString(),eq(7L))).thenReturn(List.of(Map.of("status","PENDING")));
   when(jdbc.update(anyString(),any(),any(),any())).thenReturn(1);
   var controller=new MaintenanceController(jdbc,operator,mock(MaintenanceLogService.class),mock(MiniappService.class),mock(com.laundry.api.service.PersonalDataCleanupService.class));
   assertThrows(IllegalArgumentException.class,()->controller.closeMiniappAccount(7L,new MaintenanceController.DeletionReviewRequest(false,"已核验")));
   var result=controller.closeMiniappAccount(7L,new MaintenanceController.DeletionReviewRequest(true,"已现场核验"));
   assertEquals("ACCOUNT_CLOSED",result.getData().get("status"));
   verify(jdbc).update(contains("status='ACCOUNT_CLOSED'"),eq("管理员"),eq("已现场核验"),eq(7L));
 }
}
