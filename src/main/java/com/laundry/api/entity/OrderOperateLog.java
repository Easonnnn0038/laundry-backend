package com.laundry.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单操作日志实体（全流程留痕：收衣/送厂/回店/通知取衣/取衣闭单/取消/上架/下架）
 */
@Data
@TableName("order_operate_log")
public class OrderOperateLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单ID */
    private Long orderId;

    /** 订单编号（冗余） */
    private String orderNo;

    /** 衣物明细ID（单品操作时有，如上架、单件回店） */
    private Long itemId;

    /** 衣物条码（单品操作时有） */
    private String barcode;

    /** 操作类型：RECEIVE收衣 / SEND送厂 / BACK回店 / NOTIFY通知取衣 / PICKUP取衣闭单 / CANCEL取消 / ON_SHELF上架 / OFF_SHELF下架 / ERROR_BACK错误回店 / RECHARGE会员卡充值 / DEDUCT会员卡扣款 */
    private String operateType;

    /** 操作描述（中文说明，如"订单已送厂，批次号PCxxx"） */
    private String operateDesc;

    /** 操作前订单状态 */
    private String beforeStatus;

    /** 操作后订单状态 */
    private String afterStatus;

    /** 金额变动（如扣款-52.80、充值+300） */
    private BigDecimal amountChange;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 操作时间 */
    private LocalDateTime operateTime;

    /** 备注（如取消原因） */
    private String remark;

    private LocalDateTime createTime;
}
