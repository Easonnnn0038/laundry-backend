package com.laundry.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员卡简要信息（收衣页显示用）
 */
@Data
@Schema(description = "会员卡简要信息")
public class MemberCardSimpleResponse {

    @Schema(description = "会员卡ID")
    private Long id;

    @Schema(description = "会员卡号")
    private String cardNo;

    @Schema(description = "卡类型名称：8.8折卡 / 6.8折卡")
    private String cardTypeName;

    @Schema(description = "折扣率：price_mode=1（按折扣）的类别用此比例")
    private BigDecimal discountRate;

    @Schema(description = "卡内余额")
    private BigDecimal balance;

    @Schema(description = "累计充值")
    private BigDecimal totalRecharge;

    @Schema(description = "累计消费")
    private BigDecimal totalConsume;

    @Schema(description = "状态：1正常 0停用")
    private Integer status;

    @Schema(description = "办卡时间")
    private LocalDateTime createTime;
}
