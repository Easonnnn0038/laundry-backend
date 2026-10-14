package com.laundry.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laundry.api.entity.LaundryOrder;
import com.laundry.api.entity.MiniappUser;
import com.laundry.api.entity.OrderItem;
import com.laundry.api.entity.Store;
import com.laundry.api.mapper.LaundryOrderMapper;
import com.laundry.api.mapper.MiniappUserMapper;
import com.laundry.api.mapper.OrderItemMapper;
import com.laundry.api.mapper.StoreMapper;
import com.laundry.api.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

@Service
public class MiniappService {
    private final MiniappUserMapper userMapper;
    private final LaundryOrderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final StoreMapper storeMapper;
    private final JwtUtil jwtUtil;
    private final ObjectMapper json;
    private final Environment environment;
    private final JdbcTemplate jdbc;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${app.wechat.app-id:}") private String appId;
    @Value("${app.wechat.app-secret:}") private String appSecret;
    @Value("${app.wechat.test-login-enabled:false}") private boolean testLoginEnabled;
    @Value("${app.wechat.test-login-key:}") private String testLoginKey;
    @Value("${app.wechat.test-phone:}") private String testPhone;
    private volatile String accessToken;
    private volatile long accessTokenExpiresAt;

    public MiniappService(MiniappUserMapper userMapper, LaundryOrderMapper orderMapper,
                          OrderItemMapper itemMapper, StoreMapper storeMapper,
                          JwtUtil jwtUtil, ObjectMapper json, Environment environment, JdbcTemplate jdbc) {
        this.userMapper = userMapper;
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.storeMapper = storeMapper;
        this.jwtUtil = jwtUtil;
        this.json = json;
        this.environment = environment;
        this.jdbc = jdbc;
    }

    @Transactional
    public Map<String, Object> login(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("微信登录凭证不能为空");
        requireWechatConfig();
        JsonNode response = get(UriComponentsBuilder.fromUriString("https://api.weixin.qq.com/sns/jscode2session")
                .queryParam("appid", appId).queryParam("secret", appSecret)
                .queryParam("js_code", code.trim()).queryParam("grant_type", "authorization_code")
                .build().encode().toUri());
        failOnWechatError(response, "微信登录失败");
        String openid = text(response, "openid");
        if (openid == null) throw new IllegalArgumentException("微信登录未返回用户标识");
        MiniappUser user = userMapper.selectOne(new LambdaQueryWrapper<MiniappUser>().eq(MiniappUser::getOpenid, openid));
        if (user == null) {
            user = new MiniappUser();
            user.setOpenid(openid);
            user.setUnionid(text(response, "unionid"));
            userMapper.insert(user);
        }
        return authResponse(user);
    }

    @Transactional
    public Map<String, Object> testLogin(String suppliedKey) {
        if (Arrays.asList(environment.getActiveProfiles()).contains("prod")
                || !testLoginEnabled || testLoginKey.isBlank() || testPhone.isBlank()
                || suppliedKey == null || !MessageDigest.isEqual(testLoginKey.getBytes(StandardCharsets.UTF_8), suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("测试登录不可用");
        }
        if (!testPhone.matches("1\\d{10}")) throw new IllegalStateException("测试手机号配置不正确");
        String openid = "test-" + testPhone;
        MiniappUser user = userMapper.selectOne(new LambdaQueryWrapper<MiniappUser>().eq(MiniappUser::getOpenid, openid));
        if (user == null) {
            user = new MiniappUser(); user.setOpenid(openid); user.setPhone(testPhone); user.setBindTime(LocalDateTime.now()); userMapper.insert(user);
        } else if (!testPhone.equals(user.getPhone())) {
            user.setPhone(testPhone); user.setBindTime(LocalDateTime.now()); userMapper.updateById(user);
        }
        return authResponse(user);
    }

    @Transactional
    public Map<String, Object> bindPhone(String openid, String phoneCode) {
        if (phoneCode == null || phoneCode.isBlank()) throw new IllegalArgumentException("手机号授权凭证不能为空");
        MiniappUser user = requireUser(openid);
        JsonNode body = post(URI.create("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=" + accessToken()),
                Map.of("code", phoneCode.trim()));
        failOnWechatError(body, "手机号绑定失败");
        String phone = body.path("phone_info").path("purePhoneNumber").asText(null);
        if (phone == null || !phone.matches("1\\d{10}")) throw new IllegalArgumentException("微信未返回有效手机号");
        jdbc.update("UPDATE miniapp_user SET phone=?,bind_time=NOW(),token_version=token_version+1 WHERE openid=?", phone, openid);
        return authResponse(requireUser(openid));
    }

    @Transactional
    public Map<String, Object> unbindPhone(String openid) {
        requireUser(openid);
        jdbc.update("UPDATE miniapp_user SET phone=NULL,bind_time=NULL,token_version=token_version+1 WHERE openid=?", openid);
        return authResponse(requireUser(openid));
    }

    @Transactional
    public Map<String, Object> requestDeletion(String openid) {
        if (jdbc.queryForList("SELECT id FROM miniapp_user WHERE openid=? FOR UPDATE", openid).isEmpty())
            throw new IllegalArgumentException("小程序账户不存在");
        MiniappUser user = requireUser(openid);
        ensureDeletionRequest(openid, user.getPhone());
        return Map.of("status", "PENDING", "message", "申请已登记，请到办理业务的门店核验身份后处理");
    }

    @Transactional
    public Map<String, Object> closeAccount(String openid) {
        if (jdbc.queryForList("SELECT id FROM miniapp_user WHERE openid=? FOR UPDATE", openid).isEmpty())
            throw new IllegalArgumentException("小程序账户不存在");
        MiniappUser user = requireUser(openid);
        Integer unfinished = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pickup_order p
                LEFT JOIN laundry_order o ON o.id=p.formal_order_id
                WHERE p.openid=? AND p.status<>'CANCELLED'
                  AND NOT (p.status='CONVERTED' AND o.status IN ('PICKED_UP','CANCELLED'))
                """, Integer.class, openid);
        if (unfinished != null && unfinished > 0)
            return Map.of("status", "PENDING_SERVICE", "message", "仍有未完成的预约或洗护订单。请先申请删除，服务完成后再注销账户。");
        ensureDeletionRequest(openid, user.getPhone());
        if (user.getPhone() != null) {
            Integer otherUsers = jdbc.queryForObject("SELECT COUNT(*) FROM miniapp_user WHERE phone=? AND openid<>?", Integer.class,
                    user.getPhone(), openid);
            if (otherUsers == null || otherUsers == 0)
                jdbc.update("DELETE FROM miniapp_address WHERE customer_phone=?", user.getPhone());
        }
        jdbc.update("UPDATE pickup_order SET openid=NULL WHERE openid=?", openid);
        if (jdbc.update("DELETE FROM miniapp_user WHERE openid=?", openid) != 1)
            throw new IllegalArgumentException("小程序账户不存在");
        return Map.of("status", "ACCOUNT_CLOSED", "message", "账户已注销；依法需保留的交易资料由门店核验处理");
    }

    private void ensureDeletionRequest(String openid, String phone) {
        Integer pending = jdbc.queryForObject("SELECT COUNT(*) FROM miniapp_deletion_request WHERE openid=? AND status='PENDING'", Integer.class, openid);
        if (pending == null || pending == 0)
            jdbc.update("INSERT INTO miniapp_deletion_request(openid,phone) VALUES(?,?)", openid, phone);
    }

    public List<Map<String, Object>> orders(String phone, boolean history) {
        requirePhone(phone);
        List<String> finished = List.of("PICKED_UP", "CANCELLED");
        LambdaQueryWrapper<LaundryOrder> query = new LambdaQueryWrapper<LaundryOrder>()
                .eq(LaundryOrder::getCustomerPhone, phone)
                .in(history, LaundryOrder::getStatus, finished)
                .notIn(!history, LaundryOrder::getStatus, finished)
                .orderByDesc(LaundryOrder::getReceiveTime);
        if (history) query.last("LIMIT 100");
        return orderMapper.selectList(query).stream().map(this::orderSummary).toList();
    }

    public Map<String, Object> order(String phone, Long id) {
        requirePhone(phone);
        LaundryOrder order = orderMapper.selectById(id);
        if (order == null || !phone.equals(order.getCustomerPhone())) throw new IllegalArgumentException("订单不存在");
        Map<String, Object> result = new LinkedHashMap<>(orderSummary(order));
        result.put("totalAmount", order.getTotalAmount());
        result.put("discountAmount", order.getDiscountAmount());
        result.put("urgentSurcharge", order.getUrgentSurcharge());
        result.put("totalPaid", order.getTotalPaid());
        result.put("paymentMethod", paymentLabel(order.getPaymentMethod()));
        result.put("remark", order.getRemark());
        List<Map<String, Object>> items = itemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, order.getId()).orderByAsc(OrderItem::getItemSeq))
                .stream().map(this::itemView).toList();
        result.put("items", items);
        result.put("progress", progress(order.getStatus()));
        return result;
    }

    public Map<String, Object> profile(String openid) {
        MiniappUser user = requireUser(openid);
        return Map.of("phone", user.getPhone() == null ? "" : maskPhone(user.getPhone()), "bound", user.getPhone() != null);
    }

    private Map<String, Object> authResponse(MiniappUser user) {
        String phone = user.getPhone();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("authVersion", user.getTokenVersion() == null ? 0 : user.getTokenVersion());
        if (phone != null) claims.put("phone", phone);
        String token = jwtUtil.generateToken(user.getOpenid(), "MINIAPP", claims);
        return Map.of("token", token, "bound", phone != null, "phone", phone == null ? "" : maskPhone(phone));
    }

    private Map<String, Object> orderSummary(LaundryOrder order) {
        Store store = storeMapper.selectOne(new LambdaQueryWrapper<Store>().eq(Store::getStoreCode, order.getStoreCode()));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", order.getId()); map.put("orderNo", order.getOrderNo());
        map.put("status", order.getStatus()); map.put("statusLabel", statusLabel(order.getStatus()));
        map.put("totalCount", order.getTotalCount()); map.put("receiveTime", order.getReceiveTime());
        map.put("urgent", Integer.valueOf(1).equals(order.getUrgentFlag()));
        map.put("orderType", order.getOrderType()); map.put("storeName", store == null ? order.getStoreCode() : store.getStoreName());
        map.put("storePhone", store == null ? "" : value(store.getPhone()));
        map.put("storeAddress", store == null ? "" : value(store.getAddress()));
        if (List.of("BACK_TO_STORE", "NOTIFIED", "PARTIALLY_PICKED_UP").contains(order.getStatus())) map.put("pickupCode", order.getPickupCode());
        return map;
    }

    private Map<String, Object> itemView(OrderItem item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.getId()); map.put("categoryName", item.getCategoryName());
        map.put("color", value(item.getColor())); map.put("brand", value(item.getBrand()));
        map.put("special", value(item.getSpecial())); map.put("statusLabel", itemStatus(item));
        return map;
    }

    private List<Map<String, Object>> progress(String status) {
        List<String> stages = List.of("已收衣", "工厂处理中", "返回门店", "可以取衣", "已完成");
        int active = switch (status == null ? "" : status) {
            case "RECEIVED" -> 0; case "SENT_TO_FACTORY" -> 1;
            case "STORE_REWORKING" -> 1; case "BACK_TO_STORE" -> 2;
            case "NOTIFIED", "PARTIALLY_PICKED_UP" -> 3;
            case "PICKED_UP" -> 4; case "CANCELLED" -> -1; default -> 0;
        };
        return java.util.stream.IntStream.range(0, stages.size())
                .mapToObj(i -> Map.<String, Object>of("label", stages.get(i), "done", i <= active, "current", i == active)).toList();
    }

    private String itemStatus(OrderItem item) {
        if (Integer.valueOf(2).equals(item.getShelfStatus())) return "已取走";
        if (item.getBackStoreTime() != null) return "已回店";
        if (item.getSendFactoryTime() != null) return "处理中";
        return "已收衣";
    }

    private String statusLabel(String status) { return switch (status == null ? "" : status) {
        case "RECEIVED" -> "已收衣"; case "SENT_TO_FACTORY" -> "工厂处理中";
        case "STORE_REWORKING" -> "返洗处理中";
        case "BACK_TO_STORE" -> "已回店"; case "NOTIFIED" -> "可以取衣";
        case "PARTIALLY_PICKED_UP" -> "部分取件"; case "PICKED_UP" -> "已完成";
        case "CANCELLED" -> "已取消"; default -> "状态更新中";
    }; }
    private String paymentLabel(String value) { return switch (value == null ? "" : value) {
        case "CASH" -> "现金"; case "WECHAT" -> "微信"; case "ALIPAY" -> "支付宝";
        case "MEMBER_CARD" -> "会员卡"; case "MIXED" -> "组合支付"; default -> "";
    }; }

    private MiniappUser requireUser(String openid) {
        MiniappUser user = userMapper.selectOne(new LambdaQueryWrapper<MiniappUser>().eq(MiniappUser::getOpenid, openid));
        if (user == null) throw new IllegalArgumentException("小程序登录已失效，请重新登录");
        return user;
    }
    private void requirePhone(String phone) { if (phone == null || phone.isBlank()) throw new IllegalArgumentException("请先绑定手机号"); }
    private void requireWechatConfig() { if (appId.isBlank() || appSecret.isBlank()) throw new IllegalStateException("微信小程序凭证尚未配置"); }
    private String accessToken() {
        requireWechatConfig();
        if (accessToken != null && System.currentTimeMillis() < accessTokenExpiresAt) return accessToken;
        synchronized (this) {
            if (accessToken != null && System.currentTimeMillis() < accessTokenExpiresAt) return accessToken;
            JsonNode body = send(HttpRequest.newBuilder(UriComponentsBuilder.fromUriString("https://api.weixin.qq.com/cgi-bin/token")
                    .queryParam("grant_type", "client_credential").queryParam("appid", appId).queryParam("secret", appSecret)
                    .build().encode().toUri()).timeout(Duration.ofSeconds(10)).GET().build(), true);
            failOnWechatError(body, "获取微信访问凭证失败");
            accessToken = text(body, "access_token");
            accessTokenExpiresAt = System.currentTimeMillis() + Math.max(60, body.path("expires_in").asLong(7200) - 300) * 1000;
            return accessToken;
        }
    }
    private JsonNode get(URI uri) { return send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(), false); }
    private JsonNode post(URI uri, Object body) {
        try { return send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), false); }
        catch (Exception e) { throw new IllegalArgumentException("调用微信服务失败，请稍后重试"); }
    }
    private JsonNode send(HttpRequest request, boolean safeRetry) {
        for (int attempt = 0; attempt < (safeRetry ? 2 : 1); attempt++) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (safeRetry && attempt == 0 && (response.statusCode() == 502 || response.statusCode() == 503 || response.statusCode() == 504)) continue;
            if (response.statusCode() / 100 != 2) throw new IllegalArgumentException("微信服务暂时不可用");
            return json.readTree(response.body());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalArgumentException("调用微信服务已中断"); }
        catch (IOException e) { if (safeRetry && attempt == 0) continue; throw new IllegalArgumentException("调用微信服务失败，请稍后重试"); }
        catch (Exception e) { throw new IllegalArgumentException("调用微信服务失败，请稍后重试"); }
        }
        throw new IllegalArgumentException("调用微信服务失败，请稍后重试");
    }
    private void failOnWechatError(JsonNode node, String message) {
        if (node.has("errcode") && node.path("errcode").asInt() != 0) throw new IllegalArgumentException(message + "（" + node.path("errcode").asInt() + "）");
    }
    private String text(JsonNode node, String field) { String v = node.path(field).asText(null); return v == null || v.isBlank() ? null : v; }
    private String maskPhone(String phone) { return phone.substring(0, 3) + "****" + phone.substring(7); }
    private String value(String value) { return value == null ? "" : value; }
}
