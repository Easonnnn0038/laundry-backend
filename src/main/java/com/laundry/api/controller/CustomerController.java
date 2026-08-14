package com.laundry.api.controller;

import com.laundry.api.common.Result;
import com.laundry.api.dto.request.CustomerSaveRequest;
import com.laundry.api.dto.response.CustomerResponse;
import com.laundry.api.service.CustomerService;
import com.laundry.api.utils.CurrentUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 客户控制器
 */
@Tag(name = "客户管理", description = "客户查询、保存（收衣时自动带出/新增）")
@RestController
@RequestMapping("/api/customer")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CurrentUserUtil currentUserUtil;

    @Operation(summary = "按手机号查客户", description = "收衣页输入手机号后调用，自动带出客户信息 + 会员卡详情")
    @GetMapping("/search")
    public Result<CustomerResponse> search(
            @Parameter(description = "手机号", required = true) @RequestParam String phone) {
        CustomerResponse resp = customerService.searchByPhone(phone, currentUserUtil.getStoreCode());
        return Result.success(resp);
    }

    @Operation(summary = "保存客户", description = "新客户新增，老客户按手机号+门店号更新姓名/地址/备注")
    @PostMapping("/save")
    public Result<CustomerResponse> save(@Valid @RequestBody CustomerSaveRequest request) {
        return Result.success(customerService.save(request, currentUserUtil.getStoreCode()));
    }
}
