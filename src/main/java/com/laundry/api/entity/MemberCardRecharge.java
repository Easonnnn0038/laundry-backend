package com.laundry.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员卡充值记录实体
 */
@Data
@TableName("member_card_recharge")
public class MemberCardRecharge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestId;

    /** 会员卡ID */
    private Long cardId;

    /** 会员卡号（冗余） */
    private String cardNo;

    /** 客户ID */
    private Long customerId;

    /** 客户姓名（冗余） */
    private String customerName;

    /** 充值类型：1首次办卡充值 2后续追加充值 */
    private Integer rechargeType;

    /** 充值金额 */
    private BigDecimal amount;

    /** 充值前余额 */
    private BigDecimal balanceBefore;

    /** 充值后余额 */
    private BigDecimal balanceAfter;

    /** 充值支付方式：CASH/WECHAT/ALIPAY */
    private String paymentMethod;

    /** 关联订单编号（办卡时同时收衣才有） */
    private String orderNo;

    /** 操作员ID */
    private Long operatorId;

    /** 操作员姓名 */
    private String operatorName;

    /** 备注 */
    private String remark;

    /** 充值时间 */
    private LocalDateTime createTime;
}
