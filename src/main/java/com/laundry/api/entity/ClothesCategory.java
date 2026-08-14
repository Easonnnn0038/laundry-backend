package com.laundry.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 衣物类别实体（按门店价目表6大板块重建）
 */
@Data
@TableName("clothes_category")
public class ClothesCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 板块分组：CLOTHES衣物类 / LEATHER皮衣奢饰品护理 / HOME家纺卧室 / SHOES鞋类 / IRON单烫类 / BAG包包类 */
    private String categoryGroup;

    /** 一级分类（与板块对应） */
    private String categoryLevel1;

    /** 二级分类（具体项目名称，如西装、运动鞋） */
    private String categoryLevel2;

    /** 三级分类（预留） */
    private String categoryLevel3;

    /** 原价（非会员价，价目表起步价） */
    private BigDecimal price;

    /** 计价单位：件/条/个/套/双/平方/对 */
    private String unit;

    /** 会员价模式：1=按比例折扣 2=固定会员价 */
    private Integer priceMode;

    /** 300元卡会员价（固定价模式用，折扣模式此字段为NULL） */
    private BigDecimal memberPrice300;

    /** 500元卡会员价（固定价模式用，折扣模式此字段为NULL） */
    private BigDecimal memberPrice500;

    /** 备注（如"起"表示起步价，价格可手动调整） */
    private String remark;

    /** 排序号（同板块内的显示顺序） */
    private Integer sortOrder;

    /** 状态：1-启用，0-禁用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
