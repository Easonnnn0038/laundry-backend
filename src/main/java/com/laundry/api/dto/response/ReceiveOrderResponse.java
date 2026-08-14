package com.laundry.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 提交收衣订单成功响应
 * 包含打印预览80mm小票所需的全部数据
 */
@Data
@Schema(description = "收衣订单成功响应（含打印数据）")
public class ReceiveOrderResponse {

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "14位订单编号")
    private String orderNo;

    // ========== 门店信息 ==========
    @Schema(description = "门店名称：小木棒洗衣")
    private String storeName;

    @Schema(description = "门店电话")
    private String storePhone;

    @Schema(description = "门店地址")
    private String storeAddress;

    // ========== 客户信息 ==========
    @Schema(description = "客户姓名")
    private String customerName;

    @Schema(description = "客户电话")
    private String customerPhone;

    @Schema(description = "客户地址")
    private String customerAddress;

    // ========== 会员卡信息 ==========
    @Schema(description = "是否使用了会员卡")
    private Boolean usedMemberCard;

    @Schema(description = "会员卡号")
    private String cardNo;

    @Schema(description = "卡类型")
    private String cardTypeName;

    @Schema(description = "卡扣后余额（支付后）")
    private BigDecimal cardBalanceAfter;

    // ========== 衣物明细 ==========
    @Schema(description = "衣物明细列表（含条码图片）")
    private List<ReceiveOrderItemResponse> items;

    // ========== 金额汇总 ==========
    @Schema(description = "总件数")
    private Integer totalCount;

    @Schema(description = "原价合计")
    private BigDecimal totalAmount;

    @Schema(description = "折扣率：10不打折，8.8=8.8折")
    private BigDecimal discountRate;

    @Schema(description = "优惠金额")
    private BigDecimal discountAmount;

    @Schema(description = "折扣后实付（衣物部分，加急加价前）")
    private BigDecimal actualAmount;

    @Schema(description = "是否加急：0否 1是")
    private Integer urgentFlag;

    @Schema(description = "加急加价金额（actualAmount × 20%）")
    private BigDecimal urgentSurcharge;

    @Schema(description = "瑕疵拍照数据（JSON数组字符串）")
    private String defectPhotosJson;

    @Schema(description = "新办卡充值金额（收衣时同时办卡才有）")
    private BigDecimal newCardAmount;

    @Schema(description = "充值金额（已有卡追加充值才有）")
    private BigDecimal rechargeAmount;

    @Schema(description = "本次应收总额 = 衣物实付 + 办卡充值 + 充值")
    private BigDecimal totalReceivable;

    @Schema(description = "实收金额")
    private BigDecimal totalPaid;

    @Schema(description = "欠款金额")
    private BigDecimal debtAmount;

    @Schema(description = "支付方式中文名")
    private String paymentMethodLabel;

    @Schema(description = "会员卡扣款金额")
    private BigDecimal cardDeduct;

    @Schema(description = "补差金额")
    private BigDecimal extraPayment;

    @Schema(description = "补差支付方式中文名")
    private String extraMethodLabel;

    // ========== 操作员/时间 ==========
    @Schema(description = "店员姓名")
    private String operatorName;

    @Schema(description = "收衣时间")
    private LocalDateTime receiveTime;

    @Schema(description = "订单备注")
    private String remark;
}
