package com.laundry.api.controller;

import com.laundry.api.common.Result;
import com.laundry.api.dto.request.MemberCardCreateRequest;
import com.laundry.api.dto.request.MemberCardRechargeRequest;
import com.laundry.api.dto.response.MemberCardSimpleResponse;
import com.laundry.api.dto.response.MemberCardTypeResponse;
import com.laundry.api.service.MemberCardService;
import com.laundry.api.utils.CurrentUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会员卡控制器
 */
@Tag(name = "会员卡管理", description = "办卡、查卡、充值、卡类型列表")
@RestController
@RequestMapping("/api/member-card")
public class MemberCardController {

    @Autowired
    private MemberCardService memberCardService;

    @Autowired
    private CurrentUserUtil currentUserUtil;

    @Operation(summary = "会员卡类型列表", description = "办卡弹窗选择卡类型用：8.8折卡(充300)、6.8折卡(充500)")
    @GetMapping("/types")
    public Result<List<MemberCardTypeResponse>> listTypes() {
        return Result.success(memberCardService.listCardTypes());
    }

    @Operation(summary = "按客户查会员卡", description = "客户详情页或收衣页面，根据客户ID取会员卡信息（余额、折扣率）")
    @GetMapping("/by-customer")
    public Result<MemberCardSimpleResponse> getByCustomer(
            @Parameter(description = "客户ID", required = true) @RequestParam Long customerId) {
        return Result.success(memberCardService.getByCustomerId(customerId));
    }

    @Operation(summary = "会员卡详情", description = "按会员卡ID查询详情")
    @GetMapping("/{cardId}")
    public Result<MemberCardSimpleResponse> getById(
            @Parameter(description = "会员卡ID", required = true) @PathVariable Long cardId) {
        return Result.success(memberCardService.getById(cardId));
    }

    @Operation(summary = "办理会员卡", description = "单独办卡（弹窗）。收衣页面弹窗办卡也可使用此接口，也可用订单接口的newCardFlag=1内嵌办卡")
    @PostMapping("/create")
    public Result<MemberCardSimpleResponse> create(@Valid @RequestBody MemberCardCreateRequest request) {
        return Result.success(memberCardService.createCard(
                request,
                currentUserUtil.getStoreCode(),
                currentUserUtil.getOperatorId(),
                currentUserUtil.getOperatorName()));
    }

    @Operation(summary = "会员卡充值", description = "后续追加充值（不是办卡的首次充值）")
    @PostMapping("/recharge")
    public Result<MemberCardSimpleResponse> recharge(@Valid @RequestBody MemberCardRechargeRequest request) {
        return Result.success(memberCardService.recharge(
                request,
                currentUserUtil.getOperatorId(),
                currentUserUtil.getOperatorName()));
    }
}
