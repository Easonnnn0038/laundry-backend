package com.laundry.api.dto.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 首页统计卡片数据
 * 今日订单 / 待收衣 / 待取衣 / 本月营收
 */
@Data
public class DashboardStatsResponse {

    /** 今日订单数（今日 00:00-24:00 收衣的订单数） */
    private Integer todayOrderCount;

    /** 待收衣订单数（预留：当前暂未做"预约收衣"，显示为 0） */
    private Integer pendingReceiveCount;

    /** 待取衣订单数（状态 = BACK_TO_STORE 或 NOTIFIED ：衣物已回店等待客户取走） */
    private Integer readyForPickupCount;

    /** 本月营收合计（本月1号至今，所有已收衣订单的应收 total_receivable 合计） */
    private BigDecimal monthRevenue;
}
