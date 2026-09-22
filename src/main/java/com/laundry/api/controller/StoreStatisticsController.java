package com.laundry.api.controller;

import com.laundry.api.common.Result;
import com.laundry.api.utils.CurrentUserUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/store-statistics")
public class StoreStatisticsController {
    private final JdbcTemplate jdbc;
    private final CurrentUserUtil user;
    public StoreStatisticsController(JdbcTemplate jdbc, CurrentUserUtil user) { this.jdbc=jdbc; this.user=user; }

    private void admin() {
        if (!"ADMIN".equals(user.getUser().getRole())) throw new AccessDeniedException("仅管理员可查看统计");
    }
    private void dates(LocalDate from, LocalDate to) {
        if (from==null || to==null || to.isBefore(from) || to.isAfter(from.plusYears(2)))
            throw new IllegalArgumentException("请选择不超过两年的有效日期范围");
    }

    @GetMapping("/business")
    public Result<Map<String,Object>> business(@RequestParam LocalDate from,@RequestParam LocalDate to) {
        admin(); dates(from,to);
        String store=user.getStoreCode();
        Map<String,Object> summary=jdbc.queryForMap("""
            SELECT COUNT(*) AS orderCount,COALESCE(SUM(total_count),0) AS itemCount,
                   COALESCE(SUM(total_amount),0) AS listAmount,
                   COALESCE(SUM(discount_amount),0) AS discountAmount,
                   COALESCE(SUM(urgent_surcharge),0) AS urgentAmount,
                   COALESCE(SUM(actual_amount+urgent_surcharge),0) AS serviceRevenue
            FROM laundry_order WHERE store_code=? AND status='PICKED_UP' AND cancel_flag=0
              AND pickup_time>=? AND pickup_time<?
            """,store,from.atStartOfDay(),to.plusDays(1).atStartOfDay());
        List<Map<String,Object>> daily=jdbc.queryForList("""
            SELECT DATE(pickup_time) AS day,COUNT(*) AS orderCount,SUM(total_count) AS itemCount,
                   SUM(actual_amount+urgent_surcharge) AS serviceRevenue
            FROM laundry_order WHERE store_code=? AND status='PICKED_UP' AND cancel_flag=0
              AND pickup_time>=? AND pickup_time<? GROUP BY DATE(pickup_time) ORDER BY day
            """,store,from.atStartOfDay(),to.plusDays(1).atStartOfDay());
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("summary",summary); result.put("daily",daily);
        result.put("basis","按取衣闭单日确认洗衣服务营业额；办卡、充值不计入营业额");
        return Result.success(result);
    }

    @GetMapping("/income")
    public Result<Map<String,Object>> income(@RequestParam LocalDate from,@RequestParam LocalDate to) {
        admin(); dates(from,to);
        String store=user.getStoreCode();
        // total_paid 是顾客当次实际付款；会员卡扣款在办卡/充值时已经流入，不重复计入现金收入。
        Map<String,Object> receipts=jdbc.queryForMap("""
            SELECT COUNT(*) AS orderCount,COALESCE(SUM(total_paid),0) AS collected,
              COALESCE(SUM(CASE WHEN COALESCE(new_card_amount,0)+COALESCE(recharge_amount,0)<total_paid
                THEN COALESCE(new_card_amount,0)+COALESCE(recharge_amount,0) ELSE total_paid END),0) AS cardTopup,
              COALESCE(SUM(card_deduct),0) AS cardConsumed,
              COALESCE(SUM(CASE WHEN payment_method='CASH' OR (payment_method='MIXED' AND extra_method='CASH')
                THEN total_paid ELSE 0 END),0) AS cash,
              COALESCE(SUM(CASE WHEN payment_method='WECHAT' OR (payment_method='MIXED' AND extra_method='WECHAT')
                THEN total_paid ELSE 0 END),0) AS wechat,
              COALESCE(SUM(CASE WHEN payment_method='ALIPAY' OR (payment_method='MIXED' AND extra_method='ALIPAY')
                THEN total_paid ELSE 0 END),0) AS alipay,
              COALESCE(SUM(CASE WHEN payment_method='MEMBER_CARD' THEN total_paid ELSE 0 END),0) AS methodUnspecified
            FROM laundry_order WHERE store_code=? AND receive_time>=? AND receive_time<?
            """,store,from.atStartOfDay(),to.plusDays(1).atStartOfDay());
        // 当前尚无实际退款流水；以下按全额退款政策估算应退金额，不能当作已退款现金。
        Map<String,Object> refunds=jdbc.queryForMap("""
            SELECT COUNT(*) AS cancelledCount,COALESCE(SUM(total_paid),0) AS fullRefundDue
            FROM laundry_order WHERE store_code=? AND cancel_flag=1
              AND cancel_time>=? AND cancel_time<?
            """,store,from.atStartOfDay(),to.plusDays(1).atStartOfDay());
        List<Map<String,Object>> daily=jdbc.queryForList("""
            SELECT DATE(receive_time) AS day,COUNT(*) AS orderCount,SUM(total_paid) AS collected
            FROM laundry_order WHERE store_code=? AND receive_time>=? AND receive_time<?
            GROUP BY DATE(receive_time) ORDER BY day
            """,store,from.atStartOfDay(),to.plusDays(1).atStartOfDay());
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("receipts",receipts);result.put("refunds",refunds);result.put("daily",daily);
        result.put("basis","按收衣实收日期统计资金流入；会员卡扣款只列示不重复计收入。取消订单按全额应退估算，尚无实际退款流水。");
        return Result.success(result);
    }
}
