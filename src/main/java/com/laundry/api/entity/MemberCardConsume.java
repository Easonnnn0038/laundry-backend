package com.laundry.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员卡消费记录实体（对账用：每次卡扣款/退款明细）
 */
@Data
@TableName("member_card_consume")
public class MemberCardConsume {

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

    /** 关联订单ID */
    private Long orderId;

    /** 关联订单编号 */
    private String orderNo;

    /** 消费类型：1正常扣款 2取消退款 3其他 */
    private Integer consumeType;

    /** 消费金额（正数扣款，负数退款） */
    private BigDecimal amount;

    /** 扣前余额 */
    private BigDecimal balanceBefore;

    /** 扣后余额 */
    private BigDecimal balanceAfter;

    /** 操作员ID */
    private Long operatorId;

    /** 操作员姓名 */
    private String operatorName;

    /** 备注 */
    private String remark;

    /** 消费时间 */
    private LocalDateTime createTime;
}
