package com.laundry.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 会员卡类型响应（办卡弹窗选择卡类型用）
 */
@Data
@Schema(description = "会员卡类型响应")
public class MemberCardTypeResponse {

    @Schema(description = "卡类型ID")
    private Long id;

    @Schema(description = "卡名称：8.8折卡 / 6.8折卡")
    private String name;

    @Schema(description = "充值金额（办卡时必须充值的金额）")
    private BigDecimal amount;

    @Schema(description = "折扣率：8.80 / 6.80")
    private BigDecimal discountRate;

    @Schema(description = "状态：1启用 0停用")
    private Integer status;
}
