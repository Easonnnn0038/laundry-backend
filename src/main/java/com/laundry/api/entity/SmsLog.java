package com.laundry.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短信记录实体（待接入阿里云短信）
 */
@Data
@TableName("sms_log")
public class SmsLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收手机号 */
    private String phone;

    /** 短信内容 */
    private String content;

    /** 短信类型：RECEIVE收衣通知 / BALANCE余额变动 / PICKUP取衣通知 */
    private String smsType;

    /** 关联订单ID */
    private Long orderId;

    /** 关联订单编号 */
    private String orderNo;

    /** 发送状态：0待发送 1成功 2失败 */
    private Integer sendStatus;

    /** 失败原因 */
    private String errorMsg;

    private LocalDateTime createTime;
}
