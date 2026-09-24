package com.laundry.api.controller;

import com.laundry.api.common.Result;
import com.laundry.api.dto.request.ReceiveOrderRequest;
import com.laundry.api.dto.response.DashboardRecentOrderResponse;
import com.laundry.api.dto.response.DashboardStatsResponse;
import com.laundry.api.dto.response.ReceiveOrderResponse;
import com.laundry.api.dto.response.StagingDetailResponse;
import com.laundry.api.dto.response.StagingOrderResponse;
import com.laundry.api.service.OrderService;
import com.laundry.api.utils.BarcodeUtil;
import com.laundry.api.utils.CurrentUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 订单控制器（收衣订单核心接口）
 */
@Tag(name = "收衣订单", description = "门店收衣、订单查询、暂存列表等核心接口")
@RestController
@RequestMapping("/api/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CurrentUserUtil currentUserUtil;

    @Operation(summary = "提交收衣订单（核心）",
            description = "收衣页面「确认收衣」按钮调用，事务内完成：客户保存/办卡/订单/衣物/条码/扣款/充值记录/日志/短信。返回打印预览完整数据")
    @PostMapping("/receive")
    public Result<ReceiveOrderResponse> receive(@Valid @RequestBody ReceiveOrderRequest request) {
        ReceiveOrderResponse resp = orderService.receiveOrder(
                request,
                currentUserUtil.getStoreCode(),
                currentUserUtil.getOperatorId(),
                currentUserUtil.getOperatorName());
        return Result.success(resp);
    }

    @Operation(summary = "生成Code128条码图片", description = "根据给定条码数字生成 PNG Base64 图片（带data:image前缀，可直接<img src=显示）")
    @GetMapping("/barcode")
    public Result<String> barcode(
            @Parameter(description = "条码数字", required = true) @RequestParam String code,
            @Parameter(description = "宽度像素，默认360") @RequestParam(defaultValue = "360") Integer w,
            @Parameter(description = "高度像素，默认80") @RequestParam(defaultValue = "80") Integer h) {
        return Result.success(BarcodeUtil.generateCode128DataUri(code, w, h));
    }

    // ========== 暂存列表 ==========

    @Operation(summary = "查询暂存订单列表", description = "查询待送厂/运输中/已回店的订单列表，支持按状态、门店、货架位置、客户姓名/电话筛选")
    @GetMapping("/staging-list")
    public Result<List<StagingOrderResponse>> stagingList(
            @Parameter(description = "状态：RECEIVED待送厂 / SENT_TO_FACTORY运输中 / BACK_TO_STORE已回店，不传则全部")
            @RequestParam(required = false) String status,
            @Parameter(description = "门店编号，不传则当前门店")
            @RequestParam(required = false) String storeCode,
            @Parameter(description = "货架位置搜索（如A-01）")
            @RequestParam(required = false) String shelfCode,
            @Parameter(description = "客户姓名搜索")
            @RequestParam(required = false) String customerName,
            @Parameter(description = "客户电话搜索")
            @RequestParam(required = false) String customerPhone) {
        // 如果不传门店，默认查当前门店
        String effectiveStore = storeCode;
        if (effectiveStore == null || effectiveStore.isBlank()) {
            effectiveStore = currentUserUtil.getStoreCode();
        }
        List<StagingOrderResponse> list = orderService.getStagingList(
                status, effectiveStore, shelfCode, customerName, customerPhone);
        return Result.success(list);
    }

    @Operation(summary = "查询暂存订单详情", description = "查询订单详情，包含衣物明细、瑕疵照片、货架位置等完整信息")
    @GetMapping("/staging-detail/{orderId}")
    public Result<StagingDetailResponse> stagingDetail(
            @Parameter(description = "订单ID", required = true) @PathVariable Long orderId) {
        StagingDetailResponse detail = orderService.getStagingDetail(orderId, currentUserUtil.getStoreCode());
        return Result.success(detail);
    }

    // ========== 首页 Dashboard ==========

    @Operation(summary = "首页统计数据",
            description = "返回首页 4 个数字卡片：今日订单、待收衣、待取衣、本月营收")
    @GetMapping("/dashboard-stats")
    public Result<DashboardStatsResponse> dashboardStats() {
        DashboardStatsResponse data = orderService.getDashboardStats(currentUserUtil.getStoreCode());
        return Result.success(data);
    }

    @Operation(summary = "首页最近订单",
            description = "按收衣时间倒序，返回最近 N 条订单（默认10，最多50），用于首页最近订单表格")
    @GetMapping("/dashboard-recent")
    public Result<List<DashboardRecentOrderResponse>> dashboardRecent(
            @Parameter(description = "返回条数，默认10")
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        List<DashboardRecentOrderResponse> list = orderService.getRecentOrders(
                currentUserUtil.getStoreCode(), limit);
        return Result.success(list);
    }
}
