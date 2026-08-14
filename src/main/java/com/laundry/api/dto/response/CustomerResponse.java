package com.laundry.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户信息响应（含会员卡信息，收衣页自动带出用）
 */
@Data
@Schema(description = "客户信息响应")
public class CustomerResponse {

    @Schema(description = "客户ID")
    private Long id;

    @Schema(description = "客户姓名")
    private String name;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "客户地址")
    private String address;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "门店编号")
    private String storeCode;

    @Schema(description = "累计洗衣件数")
    private Integer totalCount;

    @Schema(description = "累计消费金额")
    private BigDecimal totalAmount;

    @Schema(description = "会员卡信息（无会员卡则为null）")
    private MemberCardSimpleResponse memberCard;

    private LocalDateTime createTime;
}
