package com.laundry.api.controller;

import com.laundry.api.common.Result;
import com.laundry.api.service.ShelfService;
import com.laundry.api.utils.CurrentUserUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/shelf")
public class ShelfController {
 private final ShelfService service;private final CurrentUserUtil user;public ShelfController(ShelfService service,CurrentUserUtil user){this.service=service;this.user=user;}
 public record AllocateRequest(@NotBlank String barcode,Integer preferredShelfNo,String mode){} public record ConfirmRequest(@NotBlank String barcode,@NotNull Integer shelfNo){} public record ResizeRequest(@NotNull Integer maxShelfNo){} public record DisableRequest(@NotNull Integer shelfNo,@NotNull Boolean disabled){}
 @GetMapping("/overview")public Result<Map<String,Object>> overview(){return Result.success(service.overview(user.getStoreCode()));}
 @GetMapping("/search")public Result<List<Map<String,Object>>> search(@RequestParam String keyword){return Result.success(service.search(keyword,user.getStoreCode()));}
 @PostMapping("/allocate")public Result<Map<String,Object>> allocate(@Valid @RequestBody AllocateRequest r){return Result.success(service.allocate(r.barcode(),r.preferredShelfNo(),r.mode(),user.getStoreCode(),user.getOperatorId(),user.getOperatorName()));}
 @PostMapping("/confirm")public Result<Map<String,Object>> confirm(@Valid @RequestBody ConfirmRequest r){return Result.success(service.confirm(r.barcode(),r.shelfNo(),user.getStoreCode(),user.getOperatorId(),user.getOperatorName()));}
 @PostMapping("/cancel")public Result<Void> cancel(@RequestBody Map<String,String> r){service.cancelReservation(r.getOrDefault("barcode",""),user.getStoreCode());return Result.success();}
 @PostMapping("/resize")public Result<Void> resize(@Valid @RequestBody ResizeRequest r){service.resize(r.maxShelfNo(),user.getStoreCode(),user.getOperatorName(),"ADMIN".equals(user.getUser().getRole()));return Result.success();}
 @PostMapping("/disable")public Result<Void> disable(@Valid @RequestBody DisableRequest r){service.setDisabled(r.shelfNo(),r.disabled(),user.getStoreCode(),user.getOperatorId(),user.getOperatorName(),"ADMIN".equals(user.getUser().getRole()));return Result.success();}
}
