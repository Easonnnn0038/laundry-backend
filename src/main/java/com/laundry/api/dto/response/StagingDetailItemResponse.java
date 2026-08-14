package com.laundry.api.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 暂存详情 - 单件衣物响应
 */
@Data
public class StagingDetailItemResponse {

    /** 衣物明细ID */
    private Long id;

    /** 条码 */
    private String barcode;

    /** 序号 */
    private Integer itemSeq;

    /** 类别名称（如西装-深色） */
    private String categoryName;

    /** 颜色 */
    private String color;

    /** 品牌 */
    private String brand;

    /** 尺码 */
    private String size;

    /** 数量 */
    private Integer quantity;

    /** 单价（原价） */
    private BigDecimal unitPrice;

    /** 会员价 */
    private BigDecimal memberPrice;

    /** 小计 */
    private BigDecimal subtotal;

    /** 瑕疵备注 */
    private String defect;

    /** 特殊处理要求 */
    private String special;

    /** 货架号 */
    private String shelfCode;

    /** 上架状态：0未上架 1已上架 2已下架 */
    private Integer shelfStatus;

    /** 瑕疵照片列表 */
    private List<DefectPhotoResponse> defectPhotos;

    /** 是否送错 */
    private Integer errorBackFlag;

    /** 送错说明 */
    private String errorBackRemark;
}
