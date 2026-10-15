package com.laundry.api.controller;

import com.laundry.api.common.Result;
import com.laundry.api.dto.request.CustomerSaveRequest;
import com.laundry.api.dto.response.CustomerResponse;
import com.laundry.api.service.CustomerService;
import com.laundry.api.utils.CurrentUserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户控制器
 */
@Tag(name = "客户管理", description = "客户查询、保存（收衣时自动带出/新增）")
@RestController
@RequestMapping("/api/customer")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CurrentUserUtil currentUserUtil;

    @Autowired
    private JdbcTemplate jdbc;

    public record ContactRequest(@NotBlank String channel, @NotBlank String result,
                                 @Size(max = 500) String note) {}

    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(required = false) String keyword,
                                             @RequestParam(defaultValue = "1") int page) {
        String term = keyword == null ? "" : keyword.trim();
        if (term.length() > 50) throw new IllegalArgumentException("搜索内容不能超过50字");
        int currentPage = Math.max(1, page);
        String store = currentUserUtil.getStoreCode();
        String filter = " FROM customer c WHERE c.store_code=? AND c.phone IS NOT NULL "
                + "AND (?='' OR c.phone=? OR INSTR(c.name,?)>0)";
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + filter, Long.class,
                store, term, term, term);
        List<Map<String, Object>> records = jdbc.queryForList("""
                SELECT c.id, c.name,
                       CONCAT(LEFT(c.phone,3),'****',RIGHT(c.phone,4)) AS maskedPhone,
                       c.total_count AS totalCount, c.total_amount AS totalAmount,
                       (SELECT COUNT(*) FROM laundry_order o WHERE o.customer_id=c.id
                         AND o.store_code=c.store_code AND o.status NOT IN ('PICKED_UP','DELIVERED','CANCELLED')) AS activeOrders,
                       (SELECT MAX(o.receive_time) FROM laundry_order o WHERE o.customer_id=c.id
                         AND o.store_code=c.store_code) AS latestOrderAt,
                       (SELECT m.card_type_name FROM member_card m WHERE m.customer_id=c.id
                         AND m.store_code=c.store_code AND m.status=1 ORDER BY m.id DESC LIMIT 1) AS memberType
                """ + filter + " ORDER BY COALESCE(latestOrderAt,c.create_time) DESC,c.id DESC LIMIT 20 OFFSET ?",
                store, term, term, term, (currentPage - 1) * 20);
        return Result.success(Map.of("records", records, "total", total == null ? 0L : total,
                "page", currentPage));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable long id) {
        String store = currentUserUtil.getStoreCode();
        List<Map<String, Object>> customers = jdbc.queryForList("""
                SELECT c.id,c.name,c.phone,c.address,c.remark,c.total_count AS totalCount,
                       c.total_amount AS totalAmount,c.create_time AS createTime,
                       m.card_type_name AS memberType,m.balance AS memberBalance,m.card_no AS cardNo
                FROM customer c LEFT JOIN member_card m ON m.id=(SELECT m2.id FROM member_card m2
                    WHERE m2.customer_id=c.id AND m2.store_code=c.store_code AND m2.status=1
                    ORDER BY m2.id DESC LIMIT 1)
                WHERE c.id=? AND c.store_code=? AND c.phone IS NOT NULL
                """, id, store);
        if (customers.isEmpty()) throw new IllegalArgumentException("本门店没有该客户或资料已清理");
        List<Map<String, Object>> orders = jdbc.queryForList("""
                SELECT o.id,o.order_no AS orderNo,o.order_source AS orderSource,o.status,
                       o.total_count AS itemCount,o.total_receivable AS receivable,
                       o.total_paid AS paid,o.receive_time AS receiveTime
                FROM laundry_order o WHERE o.customer_id=? AND o.store_code=?
                ORDER BY o.receive_time DESC,o.id DESC LIMIT 50
                """, id, store);
        List<Map<String, Object>> contacts = jdbc.queryForList("""
                SELECT channel,result,note,operator_name AS operatorName,create_time AS createTime
                FROM customer_contact_log WHERE customer_id=? AND store_code=?
                ORDER BY create_time DESC,id DESC LIMIT 30
                """, id, store);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("customer", customers.get(0));
        response.put("orders", orders);
        response.put("contacts", contacts);
        return Result.success(response);
    }

    @PostMapping("/{id}/contacts")
    public Result<Map<String, Object>> addContact(@PathVariable long id,
                                                  @Valid @RequestBody ContactRequest request) {
        String store = currentUserUtil.getStoreCode();
        String channel = request.channel().trim().toUpperCase();
        String result = request.result().trim().toUpperCase();
        if (!List.of("PHONE", "WECHAT", "IN_PERSON").contains(channel)
                || !List.of("REACHED", "NO_ANSWER", "FOLLOW_UP").contains(result))
            throw new IllegalArgumentException("联系渠道或结果不正确");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM customer WHERE id=? AND store_code=? AND phone IS NOT NULL",
                Integer.class, id, store);
        if (count == null || count == 0) throw new IllegalArgumentException("本门店没有该客户或资料已清理");
        jdbc.update("""
                INSERT INTO customer_contact_log(customer_id,store_code,channel,result,note,operator_id,operator_name)
                VALUES (?,?,?,?,?,?,?)
                """, id, store, channel, result, request.note() == null ? null : request.note().trim(),
                currentUserUtil.getOperatorId(), currentUserUtil.getOperatorName());
        return detail(id);
    }

    @Operation(summary = "按手机号查客户", description = "收衣页输入手机号后调用，自动带出客户信息 + 会员卡详情")
    @GetMapping("/search")
    public Result<CustomerResponse> search(
            @Parameter(description = "手机号", required = true) @RequestParam String phone) {
        CustomerResponse resp = customerService.searchByPhone(phone, currentUserUtil.getStoreCode());
        return Result.success(resp);
    }

    @Operation(summary = "保存客户", description = "新客户新增，老客户按手机号+门店号更新姓名/地址/备注")
    @PostMapping("/save")
    public Result<CustomerResponse> save(@Valid @RequestBody CustomerSaveRequest request) {
        return Result.success(customerService.save(request, currentUserUtil.getStoreCode()));
    }
}
