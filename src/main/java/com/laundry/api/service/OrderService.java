package com.laundry.api.service;

import com.laundry.api.dto.request.ReceiveOrderRequest;
import com.laundry.api.dto.response.DashboardRecentOrderResponse;
import com.laundry.api.dto.response.DashboardStatsResponse;
import com.laundry.api.dto.response.ReceiveOrderResponse;
import com.laundry.api.dto.response.StagingDetailResponse;
import com.laundry.api.dto.response.StagingOrderResponse;

import java.util.List;

/**
 * 收衣订单服务接口（核心业务）
 */
public interface OrderService {

    /**
     * 提交收衣订单（核心接口）
     * 事务内完成：
     *   1. 客户：新客户新增 / 老客户更新
     *   2. 办卡：newCardFlag=1时创建会员卡+充值记录
     *   3. 订单：生成14位订单号 + 写入 laundry_order
     *   4. 衣物明细：生成12位条码 + 写入 order_item
     *   5. 会员卡：如果用会员卡消费，扣除卡余额 + 记录 member_card_consume
     *   6. 充值记录：办卡充值记录关联订单号
     *   7. 操作日志：order_operate_log 写入 RECEIVE/DEDUCT/RECHARGE 记录
     *   8. 短信日志：sms_log 写入收衣通知/余额变动短信（待发送标记）
     *   9. 生成所有衣物的Code128条码图片（Base64）
     *
     * @param request      收衣订单请求
     * @param storeCode    门店编号
     * @param operatorId   操作人ID
     * @param operatorName 操作人姓名
     * @return 收衣成功响应（含打印预览所有数据）
     */
    ReceiveOrderResponse receiveOrder(ReceiveOrderRequest request, String storeCode,
                                      Long operatorId, String operatorName);

    /**
     * 查询暂存订单列表
     *
     * @param status     状态筛选（RECEIVED/SENT_TO_FACTORY/BACK_TO_STORE，为空则全部）
     * @param storeCode  门店筛选（为空则全部）
     * @param shelfCode  货架位置搜索
     * @param customerName 客户姓名搜索
     * @param customerPhone 客户电话搜索
     * @return 暂存订单列表
     */
    List<StagingOrderResponse> getStagingList(String status, String storeCode,
                                              String shelfCode, String customerName,
                                              String customerPhone);

    /**
     * 查询暂存订单详情
     *
     * @param orderId 订单ID
     * @return 暂存详情（含衣物明细、瑕疵照片、货架位置）
     */
    StagingDetailResponse getStagingDetail(Long orderId);

    /**
     * 首页 4 个统计卡片数据
     *  1. 今日订单：今日 00:00 至今收衣的订单数
     *  2. 待收衣：当前业务不支持预约收衣，返回 0（预留）
     *  3. 待取衣：状态 = BACK_TO_STORE / NOTIFIED 的订单数
     *  4. 本月营收：本月 1 号至今所有已收衣订单 total_receivable 合计
     *
     * @param storeCode 门店编号（为空则全部门店）
     * @return 统计数据
     */
    DashboardStatsResponse getDashboardStats(String storeCode);

    /**
     * 首页「最近订单」列表，按收衣时间倒序取最近 limit 条
     *
     * @param storeCode 门店编号（为空则全部门店）
     * @param limit     返回条数
     * @return 最近订单列表
     */
    List<DashboardRecentOrderResponse> getRecentOrders(String storeCode, Integer limit);
}
