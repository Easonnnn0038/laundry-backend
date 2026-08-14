package com.laundry.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 会员卡办理请求（收衣弹窗办卡 / 单独办卡都可用）
 */
@Data
@Schema(description = "会员卡办理请求")
public class MemberCardCreateRequest {

    @Schema(description = "客户ID（已有客户传入）")
    private Long customerId;

    @Schema(description = "客户姓名（新客户必填）", required = true, example = "李女士")
    private String customerName;

    @Schema(description = "客户电话（必填）", required = true, example = "13999507012")
    private String customerPhone;

    @Schema(description = "客户地址（选填）")
    private String customerAddress;

    @Schema(description = "会员卡类型ID：1=8.8折卡(充300)，2=6.8折卡(充500)", required = true, example = "1")
    @NotNull(message = "会员卡类型不能为空")
    private Long cardTypeId;

    @Schema(description = "充值支付方式：CASH/WECHAT/ALIPAY", required = true, example = "CASH")
    private String paymentMethod;

    @Schema(description = "备注")
    private String remark;
}
