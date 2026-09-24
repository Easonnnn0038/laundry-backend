package com.laundry.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收衣订单实体
 */
@Data
@TableName("laundry_order")
public class LaundryOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestId;

    /** 订单编号：门店3位+日期8位YYYYMMDD+当日流水3位，共14位 */
    private String orderNo;

    private Long customerId;

    /** 客户姓名（冗余，打印小票直接用） */
    private String customerName;

    /** 客户电话（冗余） */
    private String customerPhone;

    /** 客户地址（冗余） */
    private String customerAddress;

    /** 收衣门店编号 */
    private String storeCode;

    /** 衣物总件数 */
    private Integer totalCount;

    /** 原价合计（折扣前总金额） */
    private BigDecimal totalAmount;

    /** 折扣率：10.00=不打折，8.80=8.8折，6.80=6.8折 */
    private BigDecimal discountRate;

    /** 折扣优惠金额（原价合计-折扣后合计） */
    private BigDecimal discountAmount;

    /** 折扣后应付总额（折扣后、加急加价前） */
    private BigDecimal actualAmount;

    /** 是否加急：0否 1是 */
    private Integer urgentFlag;

    /** 加急加价金额（actualAmount × 20%） */
    private BigDecimal urgentSurcharge;

    /** 瑕疵拍照数据（JSON数组，存储图片base64及元信息） */
    private String defectPhotos;

    /** 支付方式：CASH现金/WECHAT微信/ALIPAY支付宝/MEMBER_CARD会员卡/MIXED组合 */
    private String paymentMethod;

    /** 会员卡ID（使用会员卡时不为空） */
    private Long memberCardId;

    /** 会员卡号（冗余） */
    private String cardNo;

    /** 会员卡扣款金额 */
    private BigDecimal cardDeduct;

    /** 补差金额（组合支付时：实付-卡扣） */
    private BigDecimal extraPayment;

    /** 补差支付方式：CASH/WECHAT/ALIPAY */
    private String extraMethod;

    /** 是否同时办卡：0否 1是 */
    private Integer newCardFlag;

    /** 新办卡类型ID */
    private Long newCardTypeId;

    /** 新办卡充值金额（300或500） */
    private BigDecimal newCardAmount;

    /** 是否同时充值（已有卡追加充值）：0否 1是 */
    private Integer rechargeFlag;

    /** 充值金额（已有卡追加充值的金额） */
    private BigDecimal rechargeAmount;

    /** 本次应收总额 = 实付金额actual_amount + 办卡金额new_card_amount + 充值金额recharge_amount */
    private BigDecimal totalReceivable;

    /** 本次实收金额 */
    private BigDecimal totalPaid;

    /** 欠款金额 = 应收 - 实收（留痕用） */
    private BigDecimal debtAmount;

    /** 操作员ID（当前登录用户） */
    private Long operatorId;

    /** 操作员姓名（冗余，打印小票用） */
    private String operatorName;

    /** 订单状态：RECEIVED / SENT_TO_FACTORY / BACK_TO_STORE / NOTIFIED / PICKED_UP / CANCELLED */
    private String status;

    /** 是否已取消：0否 1是 */
    private Integer cancelFlag;

    /** 取消时间 */
    private LocalDateTime cancelTime;

    /** 取消操作人 */
    private String cancelOperator;

    /** 取消原因 */
    private String cancelReason;

    /** 退款比例：100.00=全退 / 90.00=退90% / 70.00=退70% */
    private BigDecimal cancelRefundRate;

    /** 实际退款金额 */
    private BigDecimal cancelRefundAmount;

    /** 退款方式（手动选择） */
    private String cancelRefundMethod;

    /** 取衣闭单时间 */
    private LocalDateTime pickupTime;

    /** 整单回店后生成的四位取衣码 */
    private String pickupCode;

    /** 取衣操作人 */
    private String pickupOperator;

    /** 订单备注（衣物整体说明） */
    private String remark;

    /** 收衣时间 */
    private LocalDateTime receiveTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
