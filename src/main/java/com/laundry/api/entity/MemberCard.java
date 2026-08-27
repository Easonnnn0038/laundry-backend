package com.laundry.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员卡实体
 */
@Data
@TableName("member_card")
public class MemberCard {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会员卡号：MC+日期8位+流水3位，如MC20260812001 */
    private String cardNo;

    /** 客户ID */
    private Long customerId;

    /** 客户姓名（冗余） */
    private String customerName;

    /** 客户电话（冗余） */
    private String customerPhone;

    /** 会员卡类型ID（关联member_card_type表） */
    private Long cardTypeId;

    /** 卡类型名称（冗余：8.8折卡/6.8折卡） */
    private String cardTypeName;

    /** 折扣率：8.80或6.80（price_mode=1时用） */
    private BigDecimal discountRate;

    /** 卡内余额（可消费余额） */
    private BigDecimal balance;

    /** 累计充值金额 */
    private BigDecimal totalRecharge;

    /** 累计消费金额 */
    private BigDecimal totalConsume;

    /** 状态：1-正常 0-停用 */
    private Integer status;

    /** 乐观锁版本号（并发扣减余额时防止丢失更新） */
    @Version
    private Integer version;

    /** 办卡门店编号 */
    private String storeCode;

    /** 办卡时间 */
    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
