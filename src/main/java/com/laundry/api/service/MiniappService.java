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
                  AND NOT (p.status='CONVERTED' AND o.status IN ('PICKED_UP','DELIVERED','CANCELLED'))
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
        List<String> finished = List.of("PICKED_UP", "DELIVERED", "CANCELLED");
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
        result.put("totalReceivable", order.getTotalReceivable());
        result.put("debtAmount", order.getDebtAmount());
        result.put("deliveryFee", order.getDeliveryFee());
        result.put("deliveryBillingStatus", order.getDeliveryBillingStatus());
        if ("HOME_DELIVERY".equals(order.getFulfillmentMethod())) {
            result.put("deliveryContactName", order.getDeliveryContactName());
            result.put("deliveryPhone", order.getDeliveryPhone());
            result.put("deliveryAddress", String.join(" ", java.util.stream.Stream.of(order.getDeliveryProvince(),
                    order.getDeliveryCity(), order.getDeliveryDistrict(), order.getDeliveryAddress())
                    .filter(java.util.Objects::nonNull).toList()));
        }
        result.put("paymentMethod", paymentLabel(order.getPaymentMethod()));
        result.put("remark", order.getRemark());
        Map<Long, String> factoryItems = "HOME_DELIVERY".equals(order.getFulfillmentMethod())
                ? jdbc.query("SELECT fis.order_item_id, fis.current_process FROM factory_item_state fis " +
                        "JOIN order_item oi ON oi.id=fis.order_item_id WHERE oi.order_id=?",
                        rs -> {
                            Map<Long, String> states = new LinkedHashMap<>();
                            while (rs.next()) states.put(rs.getLong(1), rs.getString(2));
                            return states;
                        }, order.getId()) : Map.of();
        List<Map<String, Object>> items = itemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, order.getId()).orderByAsc(OrderItem::getItemSeq))
                .stream().map(item -> itemView(item, order, factoryItems.get(item.getId()))).toList();
        result.put("items", items);
        result.put("progress", progress(order));
        if (isLocalTestOrder(order)) {
            result.put("canSimulateDelivery", "SENT_TO_FACTORY".equals(order.getStatus())
                    && deliveryStage(order).progressIndex() == 3);
            result.put("simulatedDelivery", "DELIVERED".equals(order.getStatus()));
        }
        return result;
    }

    @Transactional
    public Map<String, Object> testDeliver(String phone, Long id, String suppliedKey) {
        if (!localTestEnabled() || !testPhone.equals(phone) || suppliedKey == null
                || !MessageDigest.isEqual(testLoginKey.getBytes(StandardCharsets.UTF_8), suppliedKey.getBytes(StandardCharsets.UTF_8)))
            throw new IllegalArgumentException("本地模拟签收不可用");
        int changed = jdbc.update("""
                UPDATE laundry_order SET status='DELIVERED',delivered_time=NOW(),update_time=NOW()
                WHERE id=? AND customer_phone=? AND order_source='MINIAPP'
                  AND fulfillment_method='HOME_DELIVERY' AND status='SENT_TO_FACTORY' AND cancel_flag=0
                  AND EXISTS (SELECT 1 FROM factory_package fp WHERE fp.order_id=laundry_order.id)
                  AND NOT EXISTS (
                      SELECT 1 FROM factory_package fp WHERE fp.order_id=laundry_order.id
                        AND (fp.status<>'PACKED' OR fp.expected_item_count<>(
                          SELECT COUNT(*) FROM factory_item_state fis
                          WHERE fis.package_id=fp.id AND fis.current_process='RETURN')))
                """, id, phone);
        if (changed != 1) throw new IllegalArgumentException("仅已全部打包的本地测试订单可模拟签收，请刷新订单");
        return Map.of("status", "DELIVERED", "simulated", true);
    }

    private boolean localTestEnabled() {
        return Arrays.asList(environment.getActiveProfiles()).contains("local-miniapp")
                && !Arrays.asList(environment.getActiveProfiles()).contains("prod")
                && testLoginEnabled && !testLoginKey.isBlank() && !testPhone.isBlank();
    }

    private boolean isLocalTestOrder(LaundryOrder order) {
        return localTestEnabled() && testPhone.equals(order.getCustomerPhone())
                && "MINIAPP".equals(order.getOrderSource())
                && "HOME_DELIVERY".equals(order.getFulfillmentMethod());
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
        map.put("status", order.getStatus());
        if ("HOME_DELIVERY".equals(order.getFulfillmentMethod())) {
            DeliveryStage stage = deliveryStage(order);
            map.put("statusLabel", stage.label());
            map.put("statusHint", stage.hint());
        } else map.put("statusLabel", statusLabel(order.getStatus()));
        map.put("totalCount", order.getTotalCount()); map.put("receiveTime", order.getReceiveTime());
        map.put("urgent", Integer.valueOf(1).equals(order.getUrgentFlag()));
        map.put("orderType", order.getOrderType()); map.put("storeName", store == null ? order.getStoreCode() : store.getStoreName());
        map.put("fulfillmentMethod", order.getFulfillmentMethod());
        map.put("storePhone", store == null ? "" : value(store.getPhone()));
        map.put("storeAddress", store == null ? "" : value(store.getAddress()));
        if (List.of("BACK_TO_STORE", "NOTIFIED", "PARTIALLY_PICKED_UP").contains(order.getStatus())) map.put("pickupCode", order.getPickupCode());
        return map;
    }

    private Map<String, Object> itemView(OrderItem item, LaundryOrder order, String factoryProcess) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", item.getId()); map.put("categoryName", item.getCategoryName());
        map.put("color", value(item.getColor())); map.put("brand", value(item.getBrand()));
        map.put("special", value(item.getSpecial()));
        map.put("statusLabel", "HOME_DELIVERY".equals(order.getFulfillmentMethod())
                ? deliveryItemStatus(item, order.getStatus(), factoryProcess) : itemStatus(item));
        return map;
    }

    private List<Map<String, Object>> progress(LaundryOrder order) {
        String status = order.getStatus();
        boolean delivery = "HOME_DELIVERY".equals(order.getFulfillmentMethod());
        List<String> stages = delivery
                ? List.of("已收衣", "送往工厂", "工厂处理中", "工厂已打包", "待快递揽收", "运输中", "已签收")
                : List.of("已收衣", "工厂处理中", "返回门店", "可以取衣", "已完成");
        int active = switch (status == null ? "" : status) {
            case "RECEIVED" -> 0; case "SENT_TO_FACTORY" -> 1;
            case "STORE_REWORKING" -> 1; case "BACK_TO_STORE" -> 2;
            case "NOTIFIED", "PARTIALLY_PICKED_UP" -> 3;
            case "PICKED_UP" -> 4; case "CANCELLED" -> -1; default -> 0;
        };
        if (delivery && isLocalTestOrder(order) && "DELIVERED".equals(status)) {
            stages = List.of("已收衣", "送往工厂", "工厂处理中", "工厂已打包", "模拟已签收");
            active = 4;
        } else if (delivery) active = deliveryStage(order).progressIndex();
        final int stage = active;
        final List<String> labels = stages;
        return java.util.stream.IntStream.range(0, labels.size())
                .mapToObj(i -> Map.<String, Object>of("label", labels.get(i), "done", i <= stage, "current", i == stage)).toList();
    }

    private DeliveryStage deliveryStage(LaundryOrder order) {
        String status = order.getStatus() == null ? "" : order.getStatus();
        switch (status) {
            case "RECEIVED": return new DeliveryStage("已收衣", "门店已收衣，等待送往工厂。", 0);
            case "READY_TO_SHIP": return new DeliveryStage("待快递揽收", "费用已结清，等待快递揽收；尚无物流轨迹。", 4);
            case "SHIPPING": return new DeliveryStage("快递运输中", "暂未接入顺丰物流轨迹，无法显示包裹实时位置。", 5);
            case "DELIVERED": return isLocalTestOrder(order)
                    ? new DeliveryStage("模拟已签收", "仅用于本地测试；未发生快递揽收或真实签收，原有账单状态不变。", 4)
                    : new DeliveryStage("已签收", "订单已签收。", 6);
            case "CANCELLED": return new DeliveryStage("已取消", "订单已取消。", -1);
            case "SENT_TO_FACTORY": break;
            default: return new DeliveryStage(statusLabel(status), "请联系门店了解最新进度。", -1);
        }
        List<String> packages = jdbc.queryForList("SELECT status FROM factory_package WHERE order_id=?", String.class, order.getId());
        if (packages.isEmpty() || packages.stream().allMatch("WAIT_FACTORY"::equals))
            return new DeliveryStage("送往工厂", "门店已送厂，等待工厂确认收件。", 1);
        long packed = packages.stream().filter("PACKED"::equals).count();
        if (packed == packages.size()) {
            String billing = order.getDeliveryBillingStatus();
            String hint = switch (billing == null ? "" : billing) {
                case "AWAITING_PAYMENT" -> "工厂已打包，最终账单待支付；尚未交快递。";
                case "PAID" -> "工厂已打包、费用已结清，等待安排快递揽收。";
                default -> "工厂已打包，正在确认寄回运费；尚未交快递。";
            };
            return new DeliveryStage("工厂已打包", hint, 3);
        }
        if (packed > 0) return new DeliveryStage("部分已打包", "已打包 " + packed + "/" + packages.size() + " 个包裹，其余仍在工厂处理。", 2);
        if (packages.stream().anyMatch("FROZEN"::equals))
            return new DeliveryStage("工厂处理中", "部分包裹需要工厂核对，请联系门店了解详情。", 2);
        if (packages.stream().allMatch(s -> "QUALITY_PASSED".equals(s)))
            return new DeliveryStage("质检完成，待打包", "衣物已通过质检，等待工厂打包。", 2);
        if (packages.stream().allMatch(s -> "CHECKING".equals(s)))
            return new DeliveryStage("工厂验收中", "工厂正在核对收到的衣物。", 2);
        return new DeliveryStage("工厂处理中", "工厂正在处理衣物，完成后会更新打包进度。", 2);
    }

    private String deliveryItemStatus(OrderItem item, String orderStatus, String process) {
        if ("DELIVERED".equals(orderStatus)) return "已签收";
        if ("SHIPPING".equals(orderStatus)) return "运输中";
        if ("READY_TO_SHIP".equals(orderStatus)) return "待快递揽收";
        return switch (process == null ? "" : process) {
            case "PROCESSING", "SHOE_WASH" -> "洗护中";
            case "QUALITY" -> "质检中";
            case "PACK" -> "待打包";
            case "RETURN" -> "已打包";
            default -> item.getSendFactoryTime() == null ? "已收衣" : "送往工厂";
        };
    }

    private record DeliveryStage(String label, String hint, int progressIndex) {}

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
        case "READY_TO_SHIP" -> "待寄回"; case "SHIPPING" -> "快递运输中";
        case "DELIVERED" -> "已签收"; case "CANCELLED" -> "已取消"; default -> "状态更新中";
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
