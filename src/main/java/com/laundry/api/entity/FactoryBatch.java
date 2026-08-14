package com.laundry.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 送厂批次实体（装车送厂批量操作）
 */
@Data
@TableName("factory_batch")
public class FactoryBatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 批次号：PC+门店3位+日期8位+流水3位，如PC00120260812001 */
    private String batchNo;

    /** 门店编号 */
    private String storeCode;

    /** 送厂订单数 */
    private Integer orderCount;

    /** 送厂衣物总件数 */
    private Integer itemCount;

    /** 装车送厂操作人 */
    private String sendOperator;

    /** 送厂时间 */
    private LocalDateTime sendTime;

    /** 工厂签收人（预留） */
    private String factoryReceiver;

    /** 工厂签收时间（预留） */
    private LocalDateTime factoryReceiveTime;

    /** 状态：1送厂中 2已部分回店 3已全部回店 */
    private Integer status;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
