package com.laundry.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 会员卡充值请求
 */
@Data
@Schema(description = "会员卡充值请求")
public class MemberCardRechargeRequest {

    @Schema(description = "会员卡ID", required = true, example = "1")
    @NotNull(message = "会员卡ID不能为空")
    private Long cardId;

    @Schema(description = "充值卡类型ID（优先按此类型对应amount充值；与amount二选一必填）", example = "1")
    private Long cardTypeId;

    @Schema(description = "充值金额（或通过cardTypeId推导）", example = "300.00")
    private BigDecimal amount;

    @Schema(description = "充值支付方式：CASH/WECHAT/ALIPAY", required = true, example = "CASH")
    private String paymentMethod;

    @Schema(description = "备注")
    private String remark;
}
