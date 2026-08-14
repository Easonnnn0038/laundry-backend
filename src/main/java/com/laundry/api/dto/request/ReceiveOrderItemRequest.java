package com.laundry.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 收衣订单 - 单件衣物明细请求
 */
@Data
@Schema(description = "收衣物明细项请求")
public class ReceiveOrderItemRequest {

    @Schema(description = "衣物类别ID（clothes_category表主键）", required = true, example = "1")
    @NotNull(message = "衣物类别不能为空")
    private Long categoryId;

    @Schema(description = "数量（默认1件，一件一条码）", example = "1")
    private Integer quantity = 1;

    @Schema(description = "单价（原价，从类别带出后可手动修改）", required = true, example = "43.90")
    @NotNull(message = "单价不能为空")
    private BigDecimal unitPrice;

    @Schema(description = "会员价（折扣后的单价，前端计算后传入，后端也会重新计算校验）")
    private BigDecimal memberPrice;

    @Schema(description = "颜色", example = "白色")
    private String color;

    @Schema(description = "品牌", example = "鸭鸭")
    private String brand;

    @Schema(description = "尺码（衣物S/M/L…，鞋子35-44数字码）", example = "L")
    private String size;

    @Schema(description = "瑕疵备注", example = "边部磨破")
    private String defect;

    @Schema(description = "特殊处理要求", example = "羽绒服清洗4件；污渍尽洗")
    private String special;

    @Schema(description = "货架号/位置（收衣时录入，格式如A-01-03）", example = "A-01-03")
    private String shelfCode;
}
