package com.laundry.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员卡类型实体（卡类型配置表：300元=8.8折，500元=6.8折 等）
 */
@Data
@TableName("member_card_type")
public class MemberCardType {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 卡类型名称（如 8.8折卡、6.8折卡） */
    private String name;

    /** 办卡/充值金额（余额入账金额） */
    private BigDecimal amount;

    /** 折扣率（如 8.80 表示8.8折） */
    private BigDecimal discountRate;

    /** 状态：1启用 0停用 */
    private Integer status;

    /** 说明 */
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
