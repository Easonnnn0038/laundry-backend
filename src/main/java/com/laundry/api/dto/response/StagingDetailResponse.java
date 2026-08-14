package com.laundry.api.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 暂存详情响应
 */
@Data
public class StagingDetailResponse {

    /** 订单ID */
    private Long id;

    /** 订单编号 */
    private String orderNo;

    /** 收衣时间 */
    private LocalDateTime receiveTime;

    /** 客户姓名 */
    private String customerName;

    /** 客户电话 */
    private String customerPhone;

    /** 客户地址 */
    private String customerAddress;

    /** 订单状态 */
    private String status;

    /** 状态中文名 */
    private String statusLabel;

    /** 门店名称 */
    private String storeName;

    /** 操作员姓名 */
    private String operatorName;

    /** 衣物总件数 */
    private Integer totalCount;

    /** 原价合计 */
    private BigDecimal totalAmount;

    /** 折扣率 */
    private BigDecimal discountRate;

    /** 折扣优惠金额 */
    private BigDecimal discountAmount;

    /** 折扣后金额 */
    private BigDecimal actualAmount;

    /** 是否加急 */
    private Integer urgentFlag;

    /** 加急加价 */
    private BigDecimal urgentSurcharge;

    /** 应收合计 */
    private BigDecimal totalReceivable;

    /** 订单备注 */
    private String remark;

    /** 瑕疵照片（订单级别，JSON数组） */
    private String defectPhotosJson;

    /** 是否办卡 */
    private Integer newCardFlag;

    /** 办卡类型ID */
    private Long newCardTypeId;

    /** 办卡类型名称 */
    private String newCardTypeName;

    /** 办卡金额 */
    private BigDecimal newCardAmount;

    /** 是否同时充值（已有卡追加充值） */
    private Integer rechargeFlag;

    /** 充值金额（已有卡追加充值） */
    private BigDecimal rechargeAmount;

    /** 关联会员卡ID（卡扣时用的卡） */
    private Long memberCardId;

    /** 关联会员卡号 */
    private String memberCardNo;

    /** 卡扣金额 */
    private BigDecimal cardDeduct;

    /** 补差金额 */
    private BigDecimal extraPayment;

    /** 补差方式 */
    private String extraMethod;

    /** 衣物明细列表 */
    private List<StagingDetailItemResponse> items;
}
