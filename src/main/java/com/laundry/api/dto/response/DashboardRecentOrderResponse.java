package com.laundry.api.dto.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 首页「最近订单」条目
 */
@Data
public class DashboardRecentOrderResponse {

    /** 订单ID（操作时用） */
    private Long orderId;

    /** 取衣码（订单号最后3位，或订单号本身） */
    private String code;

    /** 客户姓名 */
    private String customer;

    /** 衣物总件数 */
    private Integer items;

    /** 订单状态英文码（RECEIVED / SENT_TO_FACTORY ...） */
    private String status;

    /** 订单状态中文显示 */
    private String statusLabel;

    /** 应收金额（total_receivable） */
    private BigDecimal amount;
}
