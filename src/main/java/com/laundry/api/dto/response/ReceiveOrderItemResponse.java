package com.laundry.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 收衣订单成功后 - 衣物明细响应（含条码图片Base64）
 */
@Data
@Schema(description = "收衣物明细响应")
public class ReceiveOrderItemResponse {

    @Schema(description = "明细ID")
    private Long id;

    @Schema(description = "衣物序号：01/02/03...")
    private Integer itemSeq;

    @Schema(description = "12位衣物条码数字")
    private String barcode;

    @Schema(description = "Code128条码图片（Base64 PNG），用于打印预览")
    private String barcodeImageBase64;

    @Schema(description = "类别名称")
    private String categoryName;

    @Schema(description = "板块分组")
    private String categoryGroup;

    @Schema(description = "数量")
    private Integer quantity;

    @Schema(description = "单价")
    private BigDecimal unitPrice;

    @Schema(description = "会员单价（折扣后）")
    private BigDecimal memberPrice;

    @Schema(description = "小计")
    private BigDecimal subtotal;

    @Schema(description = "颜色")
    private String color;

    @Schema(description = "品牌")
    private String brand;

    @Schema(description = "尺码")
    private String size;

    @Schema(description = "瑕疵备注")
    private String defect;

    @Schema(description = "特殊处理要求")
    private String special;

    @Schema(description = "货架号/位置")
    private String shelfCode;
}
