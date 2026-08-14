package com.laundry.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单衣物明细实体
 */
@Data
@TableName("order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单ID */
    private Long orderId;

    /** 订单编号（冗余） */
    private String orderNo;

    /** 衣物序号：01,02,03...（同订单内从1递增，用于生成条码） */
    private Integer itemSeq;

    /** 衣物条码：12位，门店3位+月日4位+当日流水3位+衣物序号2位 */
    private String barcode;

    /** 衣物类别ID（关联clothes_category） */
    private Long categoryId;

    /** 板块分组（冗余，如CLOTHES） */
    private String categoryGroup;

    /** 衣物类别名称（冗余：如西装、运动鞋，打印小票用） */
    private String categoryName;

    /** 数量（默认1，一般一件一条码） */
    private Integer quantity;

    /** 单价（原价，可手动修改） */
    private BigDecimal unitPrice;

    /** 会员价（折扣后的单价，记录留痕） */
    private BigDecimal memberPrice;

    /** 小计 = 单价 × 数量（折扣后金额计入订单） */
    private BigDecimal subtotal;

    /** 颜色（选填） */
    private String color;

    /** 品牌（选填） */
    private String brand;

    /** 尺码（选填，按类别：衣物S/M/L…，鞋子35-44数字码） */
    private String size;

    /** 瑕疵备注（选填） */
    private String defect;

    /** 特殊处理要求（选填，如羽绒服清洗、污渍尽洗） */
    private String special;

    /** 上架状态：0未上架 1已上架 2已下架 */
    private Integer shelfStatus;

    /** 货架号/位置（格式：字母-数字-数字，如A-01-03） */
    private String shelfCode;

    /** 上架时间 */
    private LocalDateTime onShelfTime;

    /** 下架时间 */
    private LocalDateTime offShelfTime;

    /** 错误回店标记：0正常 1工厂送错 */
    private Integer errorBackFlag;

    /** 错误回店说明 */
    private String errorBackRemark;

    /** 错误回店时间 */
    private LocalDateTime errorBackTime;

    /** 送厂批次ID（关联factory_batch表，NULL=暂存未送厂） */
    private Long batchId;

    /** 送厂批次号（冗余） */
    private String batchNo;

    /** 送厂时间 */
    private LocalDateTime sendFactoryTime;

    /** 回店时间 */
    private LocalDateTime backStoreTime;

    /** 衣物状态（暂不独立流转，保持NULL或与订单状态一致） */
    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
