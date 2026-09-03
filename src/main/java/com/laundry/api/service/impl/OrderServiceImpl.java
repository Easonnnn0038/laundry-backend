package com.laundry.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laundry.api.dto.request.ReceiveOrderItemRequest;
import com.laundry.api.dto.request.ReceiveOrderRequest;
import com.laundry.api.dto.response.ReceiveOrderItemResponse;
import com.laundry.api.dto.response.ReceiveOrderResponse;
import com.laundry.api.dto.response.*;
import com.laundry.api.entity.*;
import com.laundry.api.mapper.*;
import com.laundry.api.service.OrderService;
import com.laundry.api.mq.PrintTaskMessage;
import com.laundry.api.mq.PrintTaskProducer;
import com.laundry.api.utils.BarcodeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 收衣订单服务实现（核心业务）
 *
 * 业务规则汇总：
 * - 会员卡规则：
 *     卡类型(折扣率discount_rate)：
 *       300元卡：8.8折 -> 折扣模式(price_mode=1)类别：衣物/皮衣/家纺/鞋子/包包 按原价*8.8/10
 *                       固定价模式(price_mode=2)类别：单烫类 直接取member_price_300=8元/件
 *       500元卡：6.8折 -> 折扣模式 按原价*6.8/10
 *                       固定价模式 单烫类取member_price_500=5元/件
 *     不打折(无会员卡)：price_mode=1类别 原价；price_mode=2类别 原价（因为没卡走原价路径）
 * - 支付逻辑（自动优先卡扣兜底）：
 *     有会员卡(余额>0) -> 强制优先卡扣：
 *         余额足够：全卡扣 → MEMBER_CARD，无补差
 *         余额不足：扣光余额 + 补差 → MIXED，补差方式默认 CASH（前端传了就用传的）
 *     无会员卡 / 余额为0 -> 按前端 paymentMethod 走现金/微信/支付宝
 *         （原 MEMBER_CARD/MIXED 兜底为 CASH）
 * - 办卡同时收衣：new_card_flag=1，先办卡+充值，再用新卡扣款；办卡金额从new_card_pay_method单独付
 * - 欠款：total_paid < total_receivable，差额记debt_amount
 */
@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MMDD_FMT = DateTimeFormatter.ofPattern("MMdd");
    private static final BigDecimal BD_10 = new BigDecimal("10");
    private static final int MAX_RETRY = 3;

    @Autowired private LaundryOrderMapper orderMapper;
    @Autowired private OrderItemMapper orderItemMapper;
    @Autowired private CustomerMapper customerMapper;
    @Autowired private MemberCardMapper cardMapper;
    @Autowired private MemberCardTypeMapper cardTypeMapper;
    @Autowired private MemberCardConsumeMapper consumeMapper;
    @Autowired private MemberCardRechargeMapper rechargeMapper;
    @Autowired private ClothesCategoryMapper categoryMapper;
    @Autowired private SmsLogMapper smsLogMapper;
    @Autowired private OrderOperateLogMapper operateLogMapper;
    @Autowired private StoreMapper storeMapper;
    @Autowired private SeqCounterMapper seqCounterMapper;
    @Autowired private PrintTaskProducer printTaskProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReceiveOrderResponse receiveOrder(ReceiveOrderRequest request, String storeCode,
                                             Long operatorId, String operatorName) {
        log.info("开始收衣, 客户={}, 件数={}, 操作员={}", request.getCustomerPhone(),
                request.getItems().size(), operatorName);

        LocalDateTime now = LocalDateTime.now();
        String today = DATE_FMT.format(LocalDate.now());
        String mmdd = MMDD_FMT.format(LocalDate.now());
        List<ReceiveOrderItemRequest> itemReqs = request.getItems();

        // ========== Step 1: 客户（新客户新增 / 老客户更新） ==========
        Customer customer = resolveCustomer(request, storeCode, now);

        // ========== Step 2: 办卡（newCardFlag=1 时先办卡） ==========
        MemberCard memberCard = null;
        BigDecimal newCardAmount = BigDecimal.ZERO;
        Long newCardTypeId = null;
        if (request.getNewCardFlag() != null && request.getNewCardFlag() == 1) {
            if (request.getNewCardTypeId() == null) throw new RuntimeException("办卡需选择卡类型");

            // 先检查客户是否已有有效卡
            LambdaQueryWrapper<MemberCard> existQw = new LambdaQueryWrapper<>();
            existQw.eq(MemberCard::getCustomerId, customer.getId())
                   .eq(MemberCard::getStatus, 1)
                   .last("LIMIT 1");
            MemberCard existed = cardMapper.selectOne(existQw);

            if (existed != null) {
                // 已有卡 → 自动转为充值模式，不创建新卡
                log.info("客户已有卡 cardNo={}, 办卡自动转为充值模式", existed.getCardNo());
                memberCard = existed;
                // 把 newCardAmount 转为 rechargeAmount（走 Step 2.5 的充值逻辑）
                BigDecimal newAmt = cardTypeMapper.selectById(request.getNewCardTypeId()).getAmount();
                request.setRechargeFlag(1);
                request.setRechargeCardTypeId(request.getNewCardTypeId());
                request.setRechargePayMethod(request.getNewCardPayMethod());
                request.setNewCardFlag(0);
                request.setNewCardTypeId(null);
                request.setNewCardPayMethod(null);
                // newCardAmount 已经计入卡的流入，下面 Step 2.5 会处理充值
                newCardAmount = BigDecimal.ZERO;
            } else {
                // 无卡 → 正常办卡
                memberCard = doCreateNewCard(customer, request.getNewCardTypeId(), request.getNewCardPayMethod(),
                        storeCode, operatorId, operatorName, now);
                newCardAmount = cardTypeMapper.selectById(request.getNewCardTypeId()).getAmount();
                newCardTypeId = request.getNewCardTypeId();
            }
            request.setMemberCardId(memberCard.getId());
        } else if (request.getMemberCardId() != null) {
            // 已有会员卡
            memberCard = cardMapper.selectById(request.getMemberCardId());
            if (memberCard == null || memberCard.getStatus() != 1) {
                throw new RuntimeException("会员卡不存在或已停用");
            }
            // 安全校验：该卡的客户必须与当前客户一致，防止串卡
            if (memberCard.getCustomerId() != null && !memberCard.getCustomerId().equals(customer.getId())) {
                memberCard = null;
                request.setMemberCardId(null);
                log.warn("会员卡 customerId 与当前客户不一致，已忽略 cardId={}", request.getMemberCardId());
            }
        }

        // ========== Step 2.5: 充值（rechargeFlag=1，已有卡追加充值） ==========
        // 充值发生在卡扣计算之前：先把钱充进卡，再用充后的余额做卡扣
        BigDecimal rechargeAmount = BigDecimal.ZERO;
        BigDecimal rechargeBalanceBefore = BigDecimal.ZERO; // 充值前余额（记录用）
        if (request.getRechargeFlag() != null && request.getRechargeFlag() == 1) {
            if (memberCard == null) throw new RuntimeException("充值需已有会员卡");
            if (request.getRechargeCardTypeId() == null) throw new RuntimeException("充值需选择卡类型");
            MemberCardType rct = cardTypeMapper.selectById(request.getRechargeCardTypeId());
            if (rct == null || rct.getStatus() != 1) throw new RuntimeException("卡类型不存在或已停用");
            rechargeAmount = rct.getAmount();
            rechargeBalanceBefore = memberCard.getBalance();
            BigDecimal rechargeBalanceAfter = rechargeBalanceBefore.add(rechargeAmount);
            // 乐观锁重试：并发充值/扣款时防止余额丢失更新
            boolean rechargeUpdated = false;
            for (int retry = 0; retry < MAX_RETRY; retry++) {
                MemberCard cardToUpdate = cardMapper.selectById(memberCard.getId());
                BigDecimal currentBalance = cardToUpdate.getBalance();
                BigDecimal newBalance = currentBalance.add(rechargeAmount);
                cardToUpdate.setBalance(newBalance);
                cardToUpdate.setTotalRecharge(cardToUpdate.getTotalRecharge().add(rechargeAmount));
                cardToUpdate.setUpdateTime(now);
                int rows = cardMapper.updateById(cardToUpdate);
                if (rows > 0) {
                    rechargeBalanceAfter = newBalance;
                    memberCard.setBalance(newBalance);
                    rechargeUpdated = true;
                    break;
                }
                log.warn("会员卡充值版本冲突，重试 {}/{}", retry + 1, MAX_RETRY);
            }
            if (!rechargeUpdated) {
                throw new RuntimeException("会员卡充值失败：并发冲突，请重试");
            }
            log.info("收衣同时充值 cardNo={}, 充值={}, 充后余额={}", memberCard.getCardNo(),
                    rechargeAmount, rechargeBalanceAfter);
        }

        // ========== Step 3: 计算衣物金额（按会员卡计算每件实付） ==========
        int totalCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;  // 原价合计
        BigDecimal actualAmount = BigDecimal.ZERO; // 折扣后合计
        BigDecimal discountRate = BD_10;
        if (memberCard != null) {
            discountRate = memberCard.getDiscountRate(); // 8.80 或 6.80
        }

        List<ItemCalcResult> calcResults = new ArrayList<>();
        for (ReceiveOrderItemRequest req : itemReqs) {
            ItemCalcResult r = calcItemPrice(req, memberCard);
            calcResults.add(r);
            totalCount += (req.getQuantity() != null ? req.getQuantity() : 1);
            totalAmount = totalAmount.add(r.unitPrice.multiply(BigDecimal.valueOf(req.getQuantity() != null ? req.getQuantity() : 1)));
            actualAmount = actualAmount.add(r.subtotal);
        }
        BigDecimal discountAmount = totalAmount.subtract(actualAmount); // 优惠金额

        // ========== Step 3.5: 加急加价（urgentFlag=1 时整体加价20%） ==========
        BigDecimal urgentSurcharge = BigDecimal.ZERO;
        int urgentFlag = request.getUrgentFlag() != null ? request.getUrgentFlag() : 0;
        if (urgentFlag == 1) {
            urgentSurcharge = actualAmount.multiply(new BigDecimal("0.20"))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        // 加价后的应付金额（用于支付/卡扣计算）
        BigDecimal payableAmount = actualAmount.add(urgentSurcharge);

        // ========== Step 4: 计算会员卡扣款 + 补差 ==========
        // 核心规则：前端通过 paymentMethod 决定是否使用卡扣款
        //   - MEMBER_CARD / MIXED → 使用卡扣（自动计算卡扣金额）
        //   - CASH / WECHAT / ALIPAY → 不使用卡扣，走普通支付
        //   注意：充值场景下 memberCard 不为空，但 paymentMethod 可能是普通支付方式，
        //         此时只充值卡扣款，互不影响
        String paymentMethod = request.getPaymentMethod();
        BigDecimal cardDeduct = BigDecimal.ZERO;
        BigDecimal extraPayment = BigDecimal.ZERO;
        String extraMethod = request.getExtraMethod();

        boolean useCardForPay = "MEMBER_CARD".equals(paymentMethod) || "MIXED".equals(paymentMethod);

        if (useCardForPay && memberCard != null && memberCard.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            // === 前端选择卡扣款 + 有卡且有余额 → 自动卡扣 ===
            BigDecimal balance = memberCard.getBalance();
            if (balance.compareTo(payableAmount) >= 0) {
                // 余额足够：全卡扣
                cardDeduct = payableAmount;
                extraPayment = BigDecimal.ZERO;
                extraMethod = null;
                paymentMethod = "MEMBER_CARD";
            } else {
                // 余额不足：扣光余额 + 补差
                cardDeduct = balance;
                extraPayment = payableAmount.subtract(cardDeduct);
                paymentMethod = "MIXED";
                if (extraMethod == null || extraMethod.isBlank()) {
                    extraMethod = "CASH";
                }
            }
        } else {
            // === 不使用卡扣 → 按前端原支付方式走 ===
            cardDeduct = BigDecimal.ZERO;
            extraPayment = BigDecimal.ZERO;
            if ("MEMBER_CARD".equals(paymentMethod) || "MIXED".equals(paymentMethod)) {
                paymentMethod = "CASH";
            }
            extraMethod = null;
        }

        // ========== Step 5: 生成订单号（原子计数器，防并发冲突）==========
        String orderCounterKey = "ORDER:" + storeCode + ":" + today;
        seqCounterMapper.incrementSeq(orderCounterKey);
        int orderSeq = seqCounterMapper.getSeq(orderCounterKey);
        String orderNo = storeCode + today + String.format("%03d", orderSeq); // 14位

        // ========== Step 6: 写 laundry_order ==========
        // totalReceivable（客户实际需要掏的钱）：
        //   - 走卡扣时 = 办卡费 + 充值 + 补差（洗衣费从卡余额扣，不额外掏钱）
        //   - 不走卡扣时 = 洗衣费 + 办卡费 + 充值
        BigDecimal cardInflow = newCardAmount.add(rechargeAmount); // 流入卡内的钱（办卡+充值）
        BigDecimal totalReceivable;
        if (cardDeduct.compareTo(BigDecimal.ZERO) > 0) {
            totalReceivable = cardInflow.add(extraPayment);
        } else {
            totalReceivable = payableAmount.add(cardInflow);
        }
        BigDecimal totalPaid = request.getTotalPaid() != null ? request.getTotalPaid() : totalReceivable;
        BigDecimal debtAmount = totalReceivable.subtract(totalPaid);

        LaundryOrder order = new LaundryOrder();
        order.setOrderNo(orderNo);
        order.setCustomerId(customer.getId());
        order.setCustomerName(customer.getName());
        order.setCustomerPhone(customer.getPhone());
        order.setCustomerAddress(customer.getAddress());
        order.setStoreCode(storeCode);
        order.setTotalCount(totalCount);
        order.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        order.setDiscountRate(discountRate);
        order.setDiscountAmount(discountAmount.setScale(2, RoundingMode.HALF_UP));
        order.setActualAmount(actualAmount.setScale(2, RoundingMode.HALF_UP));
        order.setUrgentFlag(urgentFlag);
        order.setUrgentSurcharge(urgentSurcharge);
        // 瑕疵拍照（列表转JSON存库）
        try {
            if (request.getDefectPhotos() != null && !request.getDefectPhotos().isEmpty()) {
                order.setDefectPhotos(new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(request.getDefectPhotos()));
            } else {
                order.setDefectPhotos(null);
            }
        } catch (Exception e) {
            log.warn("瑕疵拍照数据序列化失败", e);
            order.setDefectPhotos(null);
        }
        order.setPaymentMethod(paymentMethod);
        if (memberCard != null) {
            order.setMemberCardId(memberCard.getId());
            order.setCardNo(memberCard.getCardNo());
        }
        order.setCardDeduct(cardDeduct.setScale(2, RoundingMode.HALF_UP));
        order.setExtraPayment(extraPayment.setScale(2, RoundingMode.HALF_UP));
        order.setExtraMethod(extraMethod);
        order.setNewCardFlag(request.getNewCardFlag() != null ? request.getNewCardFlag() : 0);
        order.setNewCardTypeId(newCardTypeId);
        order.setNewCardAmount(newCardAmount);
        order.setRechargeFlag(request.getRechargeFlag() != null ? request.getRechargeFlag() : 0);
        order.setRechargeAmount(rechargeAmount);
        order.setTotalReceivable(totalReceivable.setScale(2, RoundingMode.HALF_UP));
        order.setTotalPaid(totalPaid.setScale(2, RoundingMode.HALF_UP));
        order.setDebtAmount(debtAmount.setScale(2, RoundingMode.HALF_UP));
        order.setOperatorId(operatorId);
        order.setOperatorName(operatorName);
        order.setStatus("RECEIVED");
        order.setCancelFlag(0);
        order.setRemark(request.getRemark());
        order.setReceiveTime(now);
        order.setCreateTime(now);
        order.setUpdateTime(now);
        orderMapper.insert(order);

        // ========== Step 7: 写 order_item（生成每件12位条码） ==========
        List<ReceiveOrderItemResponse> itemResps = new ArrayList<>();
        int itemSeq = 1;
        for (ItemCalcResult cr : calcResults) {
            ReceiveOrderItemRequest req = cr.req;
            int qty = req.getQuantity() != null ? req.getQuantity() : 1;
            String barcode = storeCode + mmdd + String.format("%03d", orderSeq) + String.format("%02d", itemSeq);

            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setOrderNo(orderNo);
            item.setItemSeq(itemSeq);
            item.setBarcode(barcode);
            item.setCategoryId(cr.category.getId());
            item.setCategoryGroup(cr.category.getCategoryGroup());
            item.setCategoryName(buildCategoryLabel(cr.category));
            item.setQuantity(qty);
            item.setUnitPrice(req.getUnitPrice());
            item.setMemberPrice(cr.memberPrice);
            item.setSubtotal(cr.subtotal);
            item.setColor(req.getColor());
            item.setBrand(req.getBrand());
            item.setSize(req.getSize());
            item.setDefect(req.getDefect());
            item.setSpecial(req.getSpecial());
            item.setShelfCode(req.getShelfCode());
            item.setShelfStatus(0);
            item.setErrorBackFlag(0);
            item.setCreateTime(now);
            item.setUpdateTime(now);
            orderItemMapper.insert(item);

            // 构造响应（含条码图片）
            ReceiveOrderItemResponse ir = new ReceiveOrderItemResponse();
            ir.setId(item.getId());
            ir.setItemSeq(itemSeq);
            ir.setBarcode(barcode);
            ir.setBarcodeImageBase64(BarcodeUtil.generateCode128DataUri(barcode, 360, 80));
            ir.setCategoryName(item.getCategoryName());
            ir.setCategoryGroup(cr.category.getCategoryGroup());
            ir.setQuantity(qty);
            ir.setUnitPrice(req.getUnitPrice());
            ir.setMemberPrice(cr.memberPrice);
            ir.setSubtotal(cr.subtotal);
            ir.setColor(req.getColor());
            ir.setBrand(req.getBrand());
            ir.setSize(req.getSize());
            ir.setDefect(req.getDefect());
            ir.setSpecial(req.getSpecial());
            ir.setShelfCode(req.getShelfCode());
            itemResps.add(ir);
            itemSeq++;
        }

        // ========== Step 8: 扣会员卡余额（cardDeduct>0时）==========
        BigDecimal cardBalanceAfter = memberCard != null ? memberCard.getBalance() : null;
        if (cardDeduct.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal before = null;
            BigDecimal after = null;
            MemberCard updatedCard = null;
            boolean deductUpdated = false;
            for (int retry = 0; retry < MAX_RETRY; retry++) {
                updatedCard = cardMapper.selectById(memberCard.getId());
                before = updatedCard.getBalance();
                after = before.subtract(cardDeduct);
                if (after.compareTo(BigDecimal.ZERO) < 0) throw new RuntimeException("会员卡余额不足");

                updatedCard.setBalance(after);
                updatedCard.setTotalConsume(updatedCard.getTotalConsume().add(cardDeduct));
                updatedCard.setUpdateTime(now);
                int rows = cardMapper.updateById(updatedCard);
                if (rows > 0) {
                    cardBalanceAfter = after;
                    deductUpdated = true;
                    break;
                }
                log.warn("会员卡扣款版本冲突，重试 {}/{}", retry + 1, MAX_RETRY);
            }
            if (!deductUpdated) {
                throw new RuntimeException("会员卡扣款失败：并发冲突，请重试");
            }

            // 写消费记录
            MemberCardConsume consume = new MemberCardConsume();
            consume.setCardId(updatedCard.getId());
            consume.setCardNo(updatedCard.getCardNo());
            consume.setCustomerId(customer.getId());
            consume.setCustomerName(customer.getName());
            consume.setOrderId(order.getId());
            consume.setOrderNo(orderNo);
            consume.setConsumeType(1);
            consume.setAmount(cardDeduct);
            consume.setBalanceBefore(before);
            consume.setBalanceAfter(after);
            consume.setOperatorId(operatorId);
            consume.setOperatorName(operatorName);
            consume.setRemark("收衣订单扣款");
            consume.setCreateTime(now);
            consumeMapper.insert(consume);

            log.info("会员卡扣款 cardNo={}, 扣款={}, 余额={}", updatedCard.getCardNo(), cardDeduct, after);

            // 操作日志：DEDUCT
            writeOperateLog(order.getId(), orderNo, null, null, "DEDUCT",
                    "会员卡扣款 ¥" + cardDeduct.toPlainString() + "，余额 ¥" + after.toPlainString(),
                    null, "RECEIVED", cardDeduct.negate(), operatorId, operatorName, now, null);
        }

        // ========== Step 9: 办卡充值记录关联订单号（如果有办卡）==========
        if (newCardAmount.compareTo(BigDecimal.ZERO) > 0 && memberCard != null) {
            LambdaQueryWrapper<MemberCardRecharge> rcQw = new LambdaQueryWrapper<>();
            rcQw.eq(MemberCardRecharge::getCardId, memberCard.getId())
                .eq(MemberCardRecharge::getRechargeType, 1)
                .eq(MemberCardRecharge::getAmount, newCardAmount)
                .orderByDesc(MemberCardRecharge::getId)
                .last("LIMIT 1");
            MemberCardRecharge rc = rechargeMapper.selectOne(rcQw);
            if (rc != null && rc.getOrderNo() == null) {
                rc.setOrderNo(orderNo);
                rechargeMapper.updateById(rc);
            }
            // 操作日志：RECHARGE
            writeOperateLog(order.getId(), orderNo, null, null, "RECHARGE",
                    "办卡充值 ¥" + newCardAmount.toPlainString() + "，卡类型 " + memberCard.getCardTypeName(),
                    null, "RECEIVED", newCardAmount, operatorId, operatorName, now,
                    "支付方式：" + request.getNewCardPayMethod());
        }

        // ========== Step 9.5: 充值记录（rechargeFlag=1，已有卡追加充值） ==========
        if (rechargeAmount.compareTo(BigDecimal.ZERO) > 0 && memberCard != null) {
            MemberCardRecharge rc = new MemberCardRecharge();
            rc.setCardId(memberCard.getId());
            rc.setCardNo(memberCard.getCardNo());
            rc.setCustomerId(customer.getId());
            rc.setCustomerName(customer.getName());
            rc.setRechargeType(2); // 2=后续追加充值
            rc.setAmount(rechargeAmount);
            rc.setBalanceBefore(rechargeBalanceBefore);
            rc.setBalanceAfter(rechargeBalanceBefore.add(rechargeAmount));
            rc.setPaymentMethod(request.getRechargePayMethod() != null ? request.getRechargePayMethod() : "CASH");
            rc.setOrderNo(orderNo);
            rc.setOperatorId(operatorId);
            rc.setOperatorName(operatorName);
            rc.setRemark("收衣同时充值");
            rc.setCreateTime(now);
            rechargeMapper.insert(rc);

            // 操作日志：RECHARGE
            writeOperateLog(order.getId(), orderNo, null, null, "RECHARGE",
                    "会员卡充值 ¥" + rechargeAmount.toPlainString(),
                    null, "RECEIVED", rechargeAmount, operatorId, operatorName, now,
                    "支付方式：" + request.getRechargePayMethod());
        }

        // ========== Step 10: 更新客户累计 ==========
        customer.setTotalCount(customer.getTotalCount() + totalCount);
        customer.setTotalAmount(customer.getTotalAmount().add(payableAmount));
        customer.setUpdateTime(now);
        customerMapper.updateById(customer);

        // ========== Step 11: 操作日志 RECEIVE ==========
        writeOperateLog(order.getId(), orderNo, null, null, "RECEIVE",
                "收衣成功，订单号 " + orderNo + "，衣物 " + totalCount + " 件，应收 ¥"
                        + totalReceivable.toPlainString() + "，实收 ¥" + totalPaid.toPlainString(),
                null, "RECEIVED", actualAmount, operatorId, operatorName, now, request.getRemark());

        // ========== Step 12: 短信日志（预留，待接入阿里云）==========
        if (memberCard != null && cardDeduct.compareTo(BigDecimal.ZERO) > 0) {
            // 余额变动短信
            writeSmsLog(customer.getPhone(),
                    String.format("【小木棒洗衣】您的会员卡%s消费¥%s，剩余余额¥%s，门店：小木棒洗衣",
                            memberCard.getCardNo(), cardDeduct.toPlainString(),
                            cardBalanceAfter != null ? cardBalanceAfter.toPlainString() : "0.00"),
                    "BALANCE", order.getId(), orderNo);
        }
        // 收衣通知
        writeSmsLog(customer.getPhone(),
                String.format("【小木棒洗衣】尊敬的%s您送洗的%d件衣物已收衣，订单号%s，门店：金方世纪城民磬路108号",
                        customer.getName(), totalCount, orderNo),
                "RECEIVE", order.getId(), orderNo);

        // ========== Step 13: 构造响应（打印预览完整数据）==========
        ReceiveOrderResponse resp = new ReceiveOrderResponse();
        resp.setOrderId(order.getId());
        resp.setOrderNo(orderNo);

        // 门店信息
        Store store = storeMapper.selectOne(new LambdaQueryWrapper<Store>().eq(Store::getStoreCode, storeCode));
        if (store != null) {
            resp.setStoreName(store.getStoreName());
            resp.setStorePhone(store.getPhone());
            resp.setStoreAddress(store.getAddress());
        } else {
            resp.setStoreName("小木棒洗衣");
            resp.setStoreAddress("金方世纪城民磬路108号");
        }

        resp.setCustomerName(customer.getName());
        resp.setCustomerPhone(customer.getPhone());
        resp.setCustomerAddress(customer.getAddress());

        resp.setUsedMemberCard(memberCard != null);
        if (memberCard != null) {
            resp.setCardNo(memberCard.getCardNo());
            resp.setCardTypeName(memberCard.getCardTypeName());
            resp.setCardBalanceAfter(cardBalanceAfter);
        }

        resp.setItems(itemResps);

        resp.setTotalCount(totalCount);
        resp.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        resp.setDiscountRate(discountRate);
        resp.setDiscountAmount(discountAmount.setScale(2, RoundingMode.HALF_UP));
        resp.setActualAmount(actualAmount.setScale(2, RoundingMode.HALF_UP));
        resp.setUrgentFlag(urgentFlag);
        resp.setUrgentSurcharge(urgentSurcharge);
        resp.setDefectPhotosJson(order.getDefectPhotos());
        resp.setNewCardAmount(newCardAmount);
        resp.setRechargeAmount(rechargeAmount);
        resp.setTotalReceivable(totalReceivable.setScale(2, RoundingMode.HALF_UP));
        resp.setTotalPaid(totalPaid.setScale(2, RoundingMode.HALF_UP));
        resp.setDebtAmount(debtAmount.setScale(2, RoundingMode.HALF_UP));
        resp.setPaymentMethodLabel(paymentMethodLabel(paymentMethod));
        resp.setCardDeduct(cardDeduct.setScale(2, RoundingMode.HALF_UP));
        resp.setExtraPayment(extraPayment.setScale(2, RoundingMode.HALF_UP));
        resp.setExtraMethodLabel(paymentMethodLabel(extraMethod));
        resp.setOperatorName(operatorName);
        resp.setReceiveTime(now);
        resp.setRemark(request.getRemark());

        log.info("收衣完成 orderNo={}, totalCount={}, totalReceivable={}",
                orderNo, totalCount, totalReceivable);

        // ========== Step 14: 异步发送标签打印任务（RabbitMQ）==========
        try {
            PrintTaskMessage printTask = new PrintTaskMessage();
            printTask.setOrderNo(orderNo);
            printTask.setCustomerName(customer.getName());
            printTask.setCustomerPhone(customer.getPhone());
            printTask.setStoreCode(storeCode);
            printTask.setStoreName(resp.getStoreName() != null ? resp.getStoreName() : "小木棒洗衣");
            printTask.setCreateTime(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            List<PrintTaskMessage.ItemPrintInfo> printItems = new ArrayList<>();
            for (ReceiveOrderItemResponse ir : itemResps) {
                PrintTaskMessage.ItemPrintInfo pi = new PrintTaskMessage.ItemPrintInfo();
                pi.setBarcode(ir.getBarcode());
                pi.setCategoryName(ir.getCategoryName());
                pi.setQuantity(ir.getQuantity());
                pi.setColor(ir.getColor());
                printItems.add(pi);
            }
            printTask.setItems(printItems);

            printTaskProducer.sendPrintTask(printTask);
        } catch (Exception e) {
            log.warn("标签打印任务发送失败，不影响主流程 orderNo={}", orderNo, e);
        }

        return resp;
    }

    // =========================================================
    // 内部辅助方法
    // =========================================================

    /** 解析并保存客户信息 */
    private Customer resolveCustomer(ReceiveOrderRequest req, String storeCode, LocalDateTime now) {
        LambdaQueryWrapper<Customer> qw = new LambdaQueryWrapper<>();
        qw.eq(Customer::getPhone, req.getCustomerPhone().trim())
          .eq(Customer::getStoreCode, storeCode);
        Customer c = customerMapper.selectOne(qw);
        if (c == null) {
            c = new Customer();
            c.setName(req.getCustomerName().trim());
            c.setPhone(req.getCustomerPhone().trim());
            c.setAddress(req.getCustomerAddress());
            c.setRemark(req.getCustomerRemark());
            c.setStoreCode(storeCode);
            c.setTotalCount(0);
            c.setTotalAmount(BigDecimal.ZERO);
            c.setCreateTime(now);
            c.setUpdateTime(now);
            customerMapper.insert(c);
            log.info("收衣新增客户 id={}, name={}", c.getId(), c.getName());
        } else {
            // 更新为最新信息
            boolean changed = false;
            if (req.getCustomerName() != null && !req.getCustomerName().isBlank()
                    && !req.getCustomerName().trim().equals(c.getName())) {
                c.setName(req.getCustomerName().trim());
                changed = true;
            }
            if (req.getCustomerAddress() != null && !req.getCustomerAddress().equals(c.getAddress())) {
                c.setAddress(req.getCustomerAddress());
                changed = true;
            }
            if (req.getCustomerRemark() != null && !req.getCustomerRemark().equals(c.getRemark())) {
                c.setRemark(req.getCustomerRemark());
                changed = true;
            }
            if (changed) {
                c.setUpdateTime(now);
                customerMapper.updateById(c);
            }
        }
        return c;
    }

    /** 办新卡（返回新会员卡） */
    private MemberCard doCreateNewCard(Customer customer, Long cardTypeId, String payMethod,
                                       String storeCode, Long operatorId, String operatorName,
                                       LocalDateTime now) {
        // 一人一卡校验
        LambdaQueryWrapper<MemberCard> existQw = new LambdaQueryWrapper<>();
        existQw.eq(MemberCard::getCustomerId, customer.getId())
               .eq(MemberCard::getStatus, 1)
               .last("LIMIT 1");
        MemberCard existed = cardMapper.selectOne(existQw);
        if (existed != null) throw new RuntimeException("该客户已持有会员卡：" + existed.getCardNo());

        MemberCardType cardType = cardTypeMapper.selectById(cardTypeId);
        if (cardType == null || cardType.getStatus() != 1) throw new RuntimeException("会员卡类型不存在");

        // 卡号（原子计数器，防并发冲突）
        String today = DATE_FMT.format(LocalDate.now());
        String cardCounterKey = "CARD:" + today;
        seqCounterMapper.incrementSeq(cardCounterKey);
        int cardSeq = seqCounterMapper.getSeq(cardCounterKey);
        String cardNo = "MC" + today + String.format("%03d", cardSeq);

        MemberCard card = new MemberCard();
        card.setCardNo(cardNo);
        card.setCustomerId(customer.getId());
        card.setCustomerName(customer.getName());
        card.setCustomerPhone(customer.getPhone());
        card.setCardTypeId(cardType.getId());
        card.setCardTypeName(cardType.getName());
        card.setDiscountRate(cardType.getDiscountRate());
        card.setBalance(cardType.getAmount());
        card.setTotalRecharge(cardType.getAmount());
        card.setTotalConsume(BigDecimal.ZERO);
        card.setStatus(1);
        card.setStoreCode(storeCode);
        card.setCreateTime(now);
        card.setUpdateTime(now);
        cardMapper.insert(card);

        // 充值记录（order_no稍后订单创建好后回写）
        MemberCardRecharge rc = new MemberCardRecharge();
        rc.setCardId(card.getId());
        rc.setCardNo(cardNo);
        rc.setCustomerId(customer.getId());
        rc.setCustomerName(customer.getName());
        rc.setRechargeType(1);
        rc.setAmount(cardType.getAmount());
        rc.setBalanceBefore(BigDecimal.ZERO);
        rc.setBalanceAfter(cardType.getAmount());
        rc.setPaymentMethod(payMethod != null ? payMethod : "CASH");
        rc.setOperatorId(operatorId);
        rc.setOperatorName(operatorName);
        rc.setRemark("首次办卡充值（收衣同时办卡）");
        rc.setCreateTime(now);
        rechargeMapper.insert(rc);

        log.info("收衣同时办卡 cardNo={}, 卡类型={}", cardNo, cardType.getName());
        return card;
    }

    /** 衣物价格计算：按会员卡价格模式 */
    private ItemCalcResult calcItemPrice(ReceiveOrderItemRequest req, MemberCard card) {
        ClothesCategory cat = categoryMapper.selectById(req.getCategoryId());
        if (cat == null) throw new RuntimeException("衣物类别不存在 id=" + req.getCategoryId());
        int qty = req.getQuantity() != null ? req.getQuantity() : 1;
        BigDecimal unitPrice = req.getUnitPrice();  // 前端传的（已带出后可手动修改）

        BigDecimal memberPrice;
        if (card == null) {
            // 无会员卡：会员价字段 = 原价
            memberPrice = unitPrice;
        } else if (cat.getPriceMode() == 1) {
            // 按折扣
            BigDecimal rate = card.getDiscountRate();  // 8.80 / 6.80
            memberPrice = unitPrice.multiply(rate).divide(BD_10, 2, RoundingMode.HALF_UP);
        } else {
            // price_mode=2，固定价（单烫）
            if ("8.8折卡".equals(card.getCardTypeName()) || card.getDiscountRate().compareTo(new BigDecimal("8.80")) == 0) {
                memberPrice = cat.getMemberPrice300() != null ? cat.getMemberPrice300() : unitPrice;
            } else {
                memberPrice = cat.getMemberPrice500() != null ? cat.getMemberPrice500() : unitPrice;
            }
        }
        BigDecimal subtotal = memberPrice.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        return new ItemCalcResult(req, cat, unitPrice, memberPrice, subtotal);
    }

    /** 组装衣物显示名称（用于打印和列表） */
    private String buildCategoryLabel(ClothesCategory c) {
        if (c.getCategoryLevel1() != null && !c.getCategoryLevel1().equals(c.getCategoryLevel2())) {
            return c.getCategoryLevel1() + "-" + c.getCategoryLevel2();
        }
        return c.getCategoryLevel2();
    }

    /** 写操作日志 */
    private void writeOperateLog(Long orderId, String orderNo, Long itemId, String barcode,
                                 String type, String desc, String before, String after,
                                 BigDecimal amtChange, Long operatorId, String operatorName,
                                 LocalDateTime time, String remark) {
        OrderOperateLog l = new OrderOperateLog();
        l.setOrderId(orderId);
        l.setOrderNo(orderNo);
        l.setItemId(itemId);
        l.setBarcode(barcode);
        l.setOperateType(type);
        l.setOperateDesc(desc);
        l.setBeforeStatus(before);
        l.setAfterStatus(after);
        l.setAmountChange(amtChange);
        l.setOperatorId(operatorId);
        l.setOperatorName(operatorName);
        l.setOperateTime(time);
        l.setRemark(remark);
        l.setCreateTime(time);
        operateLogMapper.insert(l);
    }

    /** 写短信日志（预留，待接入阿里云实际发送） */
    private void writeSmsLog(String phone, String content, String type, Long orderId, String orderNo) {
        SmsLog s = new SmsLog();
        s.setPhone(phone);
        s.setContent(content);
        s.setSmsType(type);
        s.setOrderId(orderId);
        s.setOrderNo(orderNo);
        s.setSendStatus(0); // 0待发送（后续接入阿里云后改为定时任务发送）
        s.setCreateTime(LocalDateTime.now());
        smsLogMapper.insert(s);
        // 实际发送留空，待接入阿里云短信服务
    }

    /** 支付方式中文名 */
    private String paymentMethodLabel(String method) {
        if (method == null) return "";
        return switch (method) {
            case "CASH" -> "现金";
            case "WECHAT" -> "微信";
            case "ALIPAY" -> "支付宝";
            case "MEMBER_CARD" -> "会员卡";
            case "MIXED" -> "组合支付";
            default -> method;
        };
    }

    @Override
    public Integer getTodayOrderCount(String today, String storeCode) {
        // today 格式 20260812；orderNo = storeCode(3) + today(8) + seq(3)
        String prefix = storeCode + today;
        LambdaQueryWrapper<LaundryOrder> qw = new LambdaQueryWrapper<>();
        qw.likeRight(LaundryOrder::getOrderNo, prefix);
        return Math.toIntExact(orderMapper.selectCount(qw));
    }

    // =========================================================
    // 暂存列表与详情
    // =========================================================

    private static final String[] STAGING_STATUSES = {"RECEIVED", "SENT_TO_FACTORY", "BACK_TO_STORE"};

    @Override
    public List<StagingOrderResponse> getStagingList(String status, String storeCode,
                                                     String shelfCode, String customerName,
                                                     String customerPhone) {
        LambdaQueryWrapper<LaundryOrder> qw = new LambdaQueryWrapper<>();
        // 状态筛选：待送厂/运输中/已回店，排除已取衣/已取消
        if (status != null && !status.isBlank()) {
            qw.eq(LaundryOrder::getStatus, status);
        } else {
            qw.in(LaundryOrder::getStatus, (Object[]) STAGING_STATUSES);
        }
        if (storeCode != null && !storeCode.isBlank()) {
            qw.eq(LaundryOrder::getStoreCode, storeCode);
        }
        if (customerName != null && !customerName.isBlank()) {
            qw.like(LaundryOrder::getCustomerName, customerName);
        }
        if (customerPhone != null && !customerPhone.isBlank()) {
            qw.like(LaundryOrder::getCustomerPhone, customerPhone);
        }
        qw.orderByDesc(LaundryOrder::getReceiveTime);

        List<LaundryOrder> orders = orderMapper.selectList(qw);

        // 查询门店信息缓存
        List<Store> stores = storeMapper.selectList(null);
        java.util.Map<String, String> storeMap = new java.util.HashMap<>();
        for (Store s : stores) {
            storeMap.put(s.getStoreCode(), s.getStoreName());
        }

        // 查询每个订单的货架位置
        List<StagingOrderResponse> result = new ArrayList<>();
        for (LaundryOrder order : orders) {
            StagingOrderResponse resp = new StagingOrderResponse();
            resp.setId(order.getId());
            resp.setOrderNo(order.getOrderNo());
            resp.setReceiveTime(order.getReceiveTime());
            resp.setCustomerName(order.getCustomerName());
            resp.setCustomerPhone(order.getCustomerPhone());
            resp.setStatus(order.getStatus());
            resp.setStatusLabel(statusLabel(order.getStatus()));
            resp.setStoreCode(order.getStoreCode());
            resp.setStoreName(storeMap.getOrDefault(order.getStoreCode(), order.getStoreCode()));
            resp.setTotalCount(order.getTotalCount());

            // 查询衣物明细获取货架位置
            LambdaQueryWrapper<OrderItem> itemQw = new LambdaQueryWrapper<>();
            itemQw.eq(OrderItem::getOrderId, order.getId());
            List<OrderItem> items = orderItemMapper.selectList(itemQw);

            List<String> shelfCodes = new ArrayList<>();
            for (OrderItem item : items) {
                if (item.getShelfCode() != null && !item.getShelfCode().isBlank()) {
                    shelfCodes.add(item.getShelfCode());
                }
            }
            resp.setShelfCodes(shelfCodes);

            // 瑕疵照片
            resp.setHasDefectPhotos(order.getDefectPhotos() != null && !order.getDefectPhotos().isBlank());

            // 如果搜索货架位置，过滤
            if (shelfCode != null && !shelfCode.isBlank()) {
                boolean matched = false;
                for (String sc : shelfCodes) {
                    if (sc.toUpperCase().contains(shelfCode.toUpperCase())) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    // 检查是否有匹配的货架号
                    for (OrderItem item : items) {
                        if (item.getShelfCode() != null && item.getShelfCode().toUpperCase().contains(shelfCode.toUpperCase())) {
                            matched = true;
                            break;
                        }
                    }
                }
                if (!matched) continue;
            }

            result.add(resp);
        }
        return result;
    }

    @Override
    public StagingDetailResponse getStagingDetail(Long orderId) {
        LaundryOrder order = orderMapper.selectById(orderId);
        if (order == null) throw new RuntimeException("订单不存在");

        StagingDetailResponse resp = new StagingDetailResponse();
        resp.setId(order.getId());
        resp.setOrderNo(order.getOrderNo());
        resp.setReceiveTime(order.getReceiveTime());
        resp.setCustomerName(order.getCustomerName());
        resp.setCustomerPhone(order.getCustomerPhone());
        resp.setCustomerAddress(order.getCustomerAddress());
        resp.setStatus(order.getStatus());
        resp.setStatusLabel(statusLabel(order.getStatus()));
        resp.setOperatorName(order.getOperatorName());
        resp.setTotalCount(order.getTotalCount());
        resp.setTotalAmount(order.getTotalAmount());
        resp.setDiscountRate(order.getDiscountRate());
        resp.setDiscountAmount(order.getDiscountAmount());
        resp.setActualAmount(order.getActualAmount());
        resp.setUrgentFlag(order.getUrgentFlag());
        resp.setUrgentSurcharge(order.getUrgentSurcharge());
        resp.setTotalReceivable(order.getTotalReceivable());
        resp.setRemark(order.getRemark());
        resp.setDefectPhotosJson(order.getDefectPhotos());

        // 办卡信息
        resp.setNewCardFlag(order.getNewCardFlag());
        resp.setNewCardTypeId(order.getNewCardTypeId());
        resp.setNewCardAmount(order.getNewCardAmount());
        // 充值信息
        resp.setRechargeFlag(order.getRechargeFlag());
        resp.setRechargeAmount(order.getRechargeAmount());
        if (order.getNewCardTypeId() != null) {
            MemberCardType ct = cardTypeMapper.selectById(order.getNewCardTypeId());
            if (ct != null) resp.setNewCardTypeName(ct.getName());
        }

        // 会员卡信息（卡扣用的卡）
        resp.setMemberCardId(order.getMemberCardId());
        resp.setMemberCardNo(order.getCardNo());
        resp.setCardDeduct(order.getCardDeduct());
        resp.setExtraPayment(order.getExtraPayment());
        resp.setExtraMethod(order.getExtraMethod());

        // 门店名称
        Store store = storeMapper.selectOne(new LambdaQueryWrapper<Store>()
                .eq(Store::getStoreCode, order.getStoreCode()));
        resp.setStoreName(store != null ? store.getStoreName() : order.getStoreCode());

        // 查询衣物明细
        LambdaQueryWrapper<OrderItem> itemQw = new LambdaQueryWrapper<>();
        itemQw.eq(OrderItem::getOrderId, order.getId())
                .orderByAsc(OrderItem::getItemSeq);
        List<OrderItem> items = orderItemMapper.selectList(itemQw);

        // 解析订单级瑕疵照片（整个订单共享的照片）
        List<DefectPhotoResponse> orderPhotos = new ArrayList<>();
        if (order.getDefectPhotos() != null && !order.getDefectPhotos().isBlank()) {
            try {
                com.fasterxml.jackson.databind.JsonNode photos = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(order.getDefectPhotos());
                if (photos.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode photo : photos) {
                        DefectPhotoResponse dp = new DefectPhotoResponse();
                        dp.setId(photo.has("id") ? photo.get("id").asText() : null);
                        dp.setFilename(photo.has("filename") ? photo.get("filename").asText() : null);
                        dp.setUrl(photo.has("url") ? photo.get("url").asText() : null);
                        dp.setSize(photo.has("size") ? photo.get("size").asLong() : null);
                        dp.setDefectType(photo.has("defectType") && !photo.get("defectType").isNull() ? photo.get("defectType").asText() : null);
                        dp.setDefectRemark(photo.has("defectRemark") && !photo.get("defectRemark").isNull() ? photo.get("defectRemark").asText() : null);
                        orderPhotos.add(dp);
                    }
                }
            } catch (Exception e) {
                log.warn("解析订单瑕疵照片JSON失败", e);
            }
        }

        // 组装衣物明细（每件衣物独立展示瑕疵照片）
        List<StagingDetailItemResponse> itemResps = new ArrayList<>();
        for (OrderItem item : items) {
            StagingDetailItemResponse ir = new StagingDetailItemResponse();
            ir.setId(item.getId());
            ir.setBarcode(item.getBarcode());
            ir.setItemSeq(item.getItemSeq());
            ir.setCategoryName(item.getCategoryName());
            ir.setColor(item.getColor());
            ir.setBrand(item.getBrand());
            ir.setSize(item.getSize());
            ir.setQuantity(item.getQuantity());
            ir.setUnitPrice(item.getUnitPrice());
            ir.setMemberPrice(item.getMemberPrice());
            ir.setSubtotal(item.getSubtotal());
            ir.setDefect(item.getDefect());
            ir.setSpecial(item.getSpecial());
            ir.setShelfCode(item.getShelfCode());
            ir.setShelfStatus(item.getShelfStatus());
            ir.setErrorBackFlag(item.getErrorBackFlag());
            ir.setErrorBackRemark(item.getErrorBackRemark());

            // 衣物级别瑕疵照片列表
            // 如果有 order级的照片（比如收衣时拍摄的整体照片），挂到第一件衣物上
            // 每件衣物的 defectPhotos 字段目前为空，照片存在订单级别
            // 这里将订单级照片关联到所有衣物，方便展示
            if (!orderPhotos.isEmpty()) {
                ir.setDefectPhotos(new ArrayList<>(orderPhotos));
            } else {
                ir.setDefectPhotos(new ArrayList<>());
            }

            itemResps.add(ir);
        }
        resp.setItems(itemResps);
        return resp;
    }

    /** 订单状态中文名 */
    private String statusLabel(String status) {
        if (status == null) return "";
        return switch (status) {
            case "RECEIVED" -> "待送厂";
            case "SENT_TO_FACTORY" -> "运输中";
            case "BACK_TO_STORE" -> "已回店";
            case "NOTIFIED" -> "已通知取衣";
            case "PICKED_UP" -> "已取衣";
            case "CANCELLED" -> "已取消";
            default -> status;
        };
    }

    // ==================== 首页 Dashboard ====================

    @Override
    public DashboardStatsResponse getDashboardStats(String storeCode) {
        DashboardStatsResponse resp = new DashboardStatsResponse();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        try {
            // —— 1. 今日订单：今日 00:00 至今收衣订单数 ——
            LocalDateTime todayStart = today.atStartOfDay();
            LambdaQueryWrapper<LaundryOrder> todayQw = new LambdaQueryWrapper<>();
            todayQw.ge(LaundryOrder::getReceiveTime, todayStart);
            if (storeCode != null && !storeCode.isBlank()) {
                todayQw.eq(LaundryOrder::getStoreCode, storeCode);
            }
            todayQw.ne(LaundryOrder::getStatus, "CANCELLED"); // 已取消不计
            Long todayCount = orderMapper.selectCount(todayQw);
            resp.setTodayOrderCount(todayCount == null ? 0 : Math.toIntExact(todayCount));

            // —— 2. 待收衣：当前业务无预约收衣，展示 0（预留字段） ——
            resp.setPendingReceiveCount(0);

            // —— 3. 待取衣：状态 BACK_TO_STORE / NOTIFIED （衣物已回店、等待客户取走） ——
            LambdaQueryWrapper<LaundryOrder> pickupQw = new LambdaQueryWrapper<>();
            pickupQw.in(LaundryOrder::getStatus, "BACK_TO_STORE", "NOTIFIED");
            if (storeCode != null && !storeCode.isBlank()) {
                pickupQw.eq(LaundryOrder::getStoreCode, storeCode);
            }
            Long pickupCount = orderMapper.selectCount(pickupQw);
            resp.setReadyForPickupCount(pickupCount == null ? 0 : Math.toIntExact(pickupCount));

            // —— 4. 本月营收：本月 1 号 00:00 至今，所有已收衣订单（非取消）的 total_receivable 合计 ——
            // 注意：包含所有已收衣订单（无论是否取走），只要客户已付款就计入营收
            LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
            LambdaQueryWrapper<LaundryOrder> monthQw = new LambdaQueryWrapper<>();
            monthQw.ge(LaundryOrder::getReceiveTime, monthStart)
                   .ne(LaundryOrder::getStatus, "CANCELLED");
            if (storeCode != null && !storeCode.isBlank()) {
                monthQw.eq(LaundryOrder::getStoreCode, storeCode);
            }
            List<LaundryOrder> monthOrders = orderMapper.selectList(monthQw);
            BigDecimal total = BigDecimal.ZERO;
            for (LaundryOrder o : monthOrders) {
                if (o.getTotalReceivable() != null) {
                    total = total.add(o.getTotalReceivable());
                }
            }
            resp.setMonthRevenue(total);

            log.info("首页统计: 今日订单={}, 待取衣={}, 本月营收={}, 门店={}",
                    resp.getTodayOrderCount(), resp.getReadyForPickupCount(),
                    resp.getMonthRevenue(), storeCode);
        } catch (Exception e) {
            log.error("获取首页统计数据失败, storeCode={}", storeCode, e);
            // 出错时返回默认值，不抛异常给前端
            resp.setTodayOrderCount(0);
            resp.setPendingReceiveCount(0);
            resp.setReadyForPickupCount(0);
            resp.setMonthRevenue(BigDecimal.ZERO);
        }
        return resp;
    }

    @Override
    public List<DashboardRecentOrderResponse> getRecentOrders(String storeCode, Integer limit) {
        int size = (limit == null || limit <= 0) ? 10 : Math.min(limit, 50);
        LambdaQueryWrapper<LaundryOrder> qw = new LambdaQueryWrapper<>();
        if (storeCode != null && !storeCode.isBlank()) {
            qw.eq(LaundryOrder::getStoreCode, storeCode);
        }
        qw.ne(LaundryOrder::getStatus, "CANCELLED");
        qw.orderByDesc(LaundryOrder::getReceiveTime);
        qw.last("LIMIT " + size);

        List<DashboardRecentOrderResponse> result = new ArrayList<>();
        try {
            List<LaundryOrder> orders = orderMapper.selectList(qw);
            if (orders != null) {
                for (LaundryOrder o : orders) {
                    DashboardRecentOrderResponse r = new DashboardRecentOrderResponse();
                    r.setOrderId(o.getId());
                    // 取衣码：订单号（14位）作为显示，也可以截取末尾3位展示短码
                    r.setCode(o.getOrderNo());
                    r.setCustomer(o.getCustomerName());
                    r.setItems(o.getTotalCount() == null ? 0 : o.getTotalCount());
                    r.setStatus(o.getStatus());
                    r.setStatusLabel(statusLabel(o.getStatus()));
                    r.setAmount(o.getTotalReceivable() == null ? BigDecimal.ZERO : o.getTotalReceivable());
                    result.add(r);
                }
            }
        } catch (Exception e) {
            log.error("获取最近订单失败, storeCode={}, limit={}", storeCode, size, e);
        }
        return result;
    }

    /** 内部计算结果包装 */
    private static class ItemCalcResult {
        final ReceiveOrderItemRequest req;
        final ClothesCategory category;
        final BigDecimal unitPrice;
        final BigDecimal memberPrice;
        final BigDecimal subtotal;
        ItemCalcResult(ReceiveOrderItemRequest req, ClothesCategory category,
                       BigDecimal unitPrice, BigDecimal memberPrice, BigDecimal subtotal) {
            this.req = req;
            this.category = category;
            this.unitPrice = unitPrice;
            this.memberPrice = memberPrice;
            this.subtotal = subtotal;
        }
    }
}
