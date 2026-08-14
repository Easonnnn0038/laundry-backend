package com.laundry.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 衣物类别响应（含会员价，收衣选类别时用）
 */
@Data
@Schema(description = "衣物类别响应")
public class CategoryResponse {

    @Schema(description = "类别ID")
    private Long id;

    @Schema(description = "板块分组：CLOTHES/LEATHER/HOME/SHOES/IRON/BAG")
    private String categoryGroup;

    @Schema(description = "板块中文名（前端显示用）")
    private String groupLabel;

    @Schema(description = "一级分类")
    private String categoryLevel1;

    @Schema(description = "二级分类（项目名称）")
    private String categoryLevel2;

    @Schema(description = "原价")
    private BigDecimal price;

    /** 原价（与price相同字段，兼容前端originalPrice引用） */
    @Schema(description = "原价（与price相同）")
    private BigDecimal originalPrice;

    @Schema(description = "计价单位")
    private String unit;

    @Schema(description = "会员价模式：1按折扣 2固定价")
    private Integer priceMode;

    @Schema(description = "300元卡会员价（折扣模式下由前端按折扣率实时计算，固定价模式直接取）")
    private BigDecimal memberPrice300;

    @Schema(description = "500元卡会员价（折扣模式下由前端按折扣率实时计算，固定价模式直接取）")
    private BigDecimal memberPrice500;

    @Schema(description = "备注（如起表示起步价）")
    private String remark;

    @Schema(description = "排序号")
    private Integer sortOrder;
}
