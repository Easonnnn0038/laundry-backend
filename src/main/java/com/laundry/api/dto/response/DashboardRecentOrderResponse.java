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

    /** 业务订单号；不是回店后才生成的四位取衣码。 */
    private String orderNo;

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
