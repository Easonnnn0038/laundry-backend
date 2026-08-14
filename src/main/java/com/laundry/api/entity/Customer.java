package com.laundry.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户实体
 */
@Data
@TableName("customer")
public class Customer {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户姓名（必填） */
    private String name;

    /** 联系电话（必填，用于自动带出客户信息） */
    private String phone;

    /** 客户地址（选填） */
    private String address;

    /** 备注信息（选填） */
    private String remark;

    /** 所属门店编号 */
    private String storeCode;

    /** 累计洗衣件数（统计用） */
    private Integer totalCount;

    /** 累计消费金额（统计用） */
    private BigDecimal totalAmount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
