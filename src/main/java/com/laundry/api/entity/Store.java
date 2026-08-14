package com.laundry.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店实体
 */
@Data
@TableName("store")
public class Store {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 门店编号 */
    private String storeCode;

    /** 门店名称 */
    private String storeName;

    /** 联系电话 */
    private String phone;

    /** 门店地址 */
    private String address;

    /** 创建时间 */
    private LocalDateTime createTime;
}
