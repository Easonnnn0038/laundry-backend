package com.laundry.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laundry.api.dto.request.MemberCardCreateRequest;
import com.laundry.api.dto.request.MemberCardRechargeRequest;
import com.laundry.api.dto.response.MemberCardSimpleResponse;
import com.laundry.api.dto.response.MemberCardTypeResponse;
import com.laundry.api.entity.Customer;
import com.laundry.api.entity.MemberCard;
import com.laundry.api.entity.MemberCardRecharge;
import com.laundry.api.entity.MemberCardType;
import com.laundry.api.mapper.CustomerMapper;
import com.laundry.api.mapper.MemberCardMapper;
import com.laundry.api.mapper.MemberCardRechargeMapper;
import com.laundry.api.mapper.MemberCardTypeMapper;
import com.laundry.api.mapper.SeqCounterMapper;
import com.laundry.api.service.MemberCardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 会员卡服务实现
 */
@Service
public class MemberCardServiceImpl implements MemberCardService {

    private static final Logger log = LoggerFactory.getLogger(MemberCardServiceImpl.class);
    private static final int MAX_RETRY = 3;

    @Autowired
    private MemberCardTypeMapper cardTypeMapper;

    @Autowired
    private MemberCardMapper cardMapper;

    @Autowired
    private MemberCardRechargeMapper rechargeMapper;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private SeqCounterMapper seqCounterMapper;

    @Override
    public List<MemberCardTypeResponse> listCardTypes() {
        LambdaQueryWrapper<MemberCardType> qw = new LambdaQueryWrapper<>();
        qw.eq(MemberCardType::getStatus, 1)
          .orderByAsc(MemberCardType::getId);
        return cardTypeMapper.selectList(qw).stream()
                .map(this::toTypeResponse)
                .collect(Collectors.toList());
    }

    @Override
    public MemberCardSimpleResponse getByCustomerId(Long customerId) {
        if (customerId == null) return null;
        LambdaQueryWrapper<MemberCard> qw = new LambdaQueryWrapper<>();
        qw.eq(MemberCard::getCustomerId, customerId)
          .eq(MemberCard::getStatus, 1);
        MemberCard card = cardMapper.selectOne(qw);
        return card == null ? null : toSimpleResponse(card);
    }

    @Override
    public MemberCardSimpleResponse getById(Long cardId) {
        if (cardId == null) return null;
        MemberCard card = cardMapper.selectById(cardId);
        if (card == null || card.getStatus() != 1) return null;
        return toSimpleResponse(card);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberCardSimpleResponse createCard(MemberCardCreateRequest request, String storeCode,
                                               Long operatorId, String operatorName) {
        // 1. 查找客户ID（没有就新增）
        Long customerId = request.getCustomerId();
        if (customerId == null) {
            // 按手机号查
            LambdaQueryWrapper<Customer> cqw = new LambdaQueryWrapper<>();
            cqw.eq(Customer::getPhone, request.getCustomerPhone().trim())
               .eq(Customer::getStoreCode, storeCode);
            Customer c = customerMapper.selectOne(cqw);
            if (c == null) {
                c = new Customer();
                c.setName(request.getCustomerName() != null ? request.getCustomerName().trim() : "新客户");
                c.setPhone(request.getCustomerPhone().trim());
                c.setAddress(request.getCustomerAddress());
                c.setStoreCode(storeCode);
                c.setTotalCount(0);
                c.setTotalAmount(BigDecimal.ZERO);
                c.setCreateTime(LocalDateTime.now());
                c.setUpdateTime(LocalDateTime.now());
                customerMapper.insert(c);
                log.info("办卡时新增客户 id={}, name={}", c.getId(), c.getName());
            }
            customerId = c.getId();
        }
        Customer customer = customerMapper.selectById(customerId);

        // 2. 校验：该客户是否已有正常卡（一人一卡）
        LambdaQueryWrapper<MemberCard> existQw = new LambdaQueryWrapper<>();
        existQw.eq(MemberCard::getCustomerId, customerId)
               .eq(MemberCard::getStatus, 1);
        MemberCard existing = cardMapper.selectOne(existQw);
        if (existing != null) {
            throw new RuntimeException("该客户已有生效会员卡，卡号：" + existing.getCardNo());
        }

        // 3. 查卡类型
        MemberCardType cardType = cardTypeMapper.selectById(request.getCardTypeId());
        if (cardType == null || cardType.getStatus() != 1) {
            throw new RuntimeException("会员卡类型不存在或已停用");
        }

        // 4. 生成会员卡号：MC + 日期(YYYYMMDD8位) + 流水3位（原子计数器，防并发冲突）
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String cardCounterKey = "CARD:" + today;
        seqCounterMapper.incrementSeq(cardCounterKey);
        int seq = seqCounterMapper.getSeq(cardCounterKey);
        String cardNo = "MC" + today + String.format("%03d", seq);

        // 5. 写入会员卡
        BigDecimal rechargeAmount = cardType.getAmount(); // 办卡金额 = 卡类型 amount
        MemberCard card = new MemberCard();
        card.setCardNo(cardNo);
        card.setCustomerId(customerId);
        card.setCustomerName(customer.getName());
        card.setCustomerPhone(customer.getPhone());
        card.setCardTypeId(cardType.getId());
        card.setCardTypeName(cardType.getName());
        card.setDiscountRate(cardType.getDiscountRate());
        card.setBalance(rechargeAmount);                 // 充值金额直接进余额
        card.setTotalRecharge(rechargeAmount);
        card.setTotalConsume(BigDecimal.ZERO);
        card.setStatus(1);
        card.setStoreCode(storeCode);
        card.setCreateTime(LocalDateTime.now());
        card.setUpdateTime(LocalDateTime.now());
        cardMapper.insert(card);

        // 6. 写入充值记录（首次办卡充值）
        MemberCardRecharge rc = new MemberCardRecharge();
        rc.setCardId(card.getId());
        rc.setCardNo(cardNo);
        rc.setCustomerId(customerId);
        rc.setCustomerName(customer.getName());
        rc.setRechargeType(1); // 1首次办卡
        rc.setAmount(rechargeAmount);
        rc.setBalanceBefore(BigDecimal.ZERO);
        rc.setBalanceAfter(rechargeAmount);
        // 兜底：支付方式空则默认现金
        String payMethod = request.getPaymentMethod();
        if (payMethod == null || payMethod.trim().isEmpty()) payMethod = "CASH";
        rc.setPaymentMethod(payMethod);
        rc.setOperatorId(operatorId);
        rc.setOperatorName(operatorName);
        rc.setRemark(request.getRemark() != null ? request.getRemark() : "首次办卡充值");
        rc.setCreateTime(LocalDateTime.now());
        rechargeMapper.insert(rc);

        log.info("办卡成功 cardNo={}, customer={}, 充值={}", cardNo, customer.getName(), rechargeAmount);
        return toSimpleResponse(card);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberCardSimpleResponse recharge(MemberCardRechargeRequest request,
                                             Long operatorId, String operatorName) {
        MemberCard card = cardMapper.selectById(request.getCardId());
        if (card == null || card.getStatus() != 1) {
            throw new RuntimeException("会员卡不存在或已停用");
        }

        // 充值金额：优先使用前端传入的 amount；若没传则根据 cardTypeId 推导
        BigDecimal amount = request.getAmount();
        if ((amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) && request.getCardTypeId() != null) {
            MemberCardType t = cardTypeMapper.selectById(request.getCardTypeId());
            if (t == null || t.getStatus() != 1) throw new RuntimeException("充值类型不存在");
            amount = t.getAmount();
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("请输入或选择充值金额");
        }

        BigDecimal before = null;
        BigDecimal after = null;
        boolean updated = false;
        for (int retry = 0; retry < MAX_RETRY; retry++) {
            MemberCard freshCard = cardMapper.selectById(request.getCardId());
            before = freshCard.getBalance();
            after = before.add(amount);
            freshCard.setBalance(after);
            freshCard.setTotalRecharge(freshCard.getTotalRecharge().add(amount));
            freshCard.setUpdateTime(LocalDateTime.now());
            int rows = cardMapper.updateById(freshCard);
            if (rows > 0) {
                card = freshCard;
                updated = true;
                break;
            }
            log.warn("会员卡充值版本冲突，重试 {}/{}", retry + 1, MAX_RETRY);
        }
        if (!updated) {
            throw new RuntimeException("会员卡充值失败：并发冲突，请重试");
        }

        // 写充值记录
        MemberCardRecharge rc = new MemberCardRecharge();
        rc.setCardId(card.getId());
        rc.setCardNo(card.getCardNo());
        rc.setCustomerId(card.getCustomerId());
        rc.setCustomerName(card.getCustomerName());
        rc.setRechargeType(2); // 2后续追加
        rc.setAmount(amount);
        rc.setBalanceBefore(before);
        rc.setBalanceAfter(after);
        rc.setPaymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : "CASH");
        rc.setOperatorId(operatorId);
        rc.setOperatorName(operatorName);
        rc.setRemark(request.getRemark());
        rc.setCreateTime(LocalDateTime.now());
        rechargeMapper.insert(rc);

        log.info("会员卡充值 cardNo={}, 金额={}, 充值后余额={}", card.getCardNo(), amount, after);
        return toSimpleResponse(card);
    }

    // ---------- 内部转换方法 ----------
    private MemberCardTypeResponse toTypeResponse(MemberCardType t) {
        MemberCardTypeResponse r = new MemberCardTypeResponse();
        r.setId(t.getId());
        r.setName(t.getName());
        r.setAmount(t.getAmount());
        r.setDiscountRate(t.getDiscountRate());
        r.setStatus(t.getStatus());
        return r;
    }

    private MemberCardSimpleResponse toSimpleResponse(MemberCard c) {
        MemberCardSimpleResponse r = new MemberCardSimpleResponse();
        r.setId(c.getId());
        r.setCardNo(c.getCardNo());
        r.setCardTypeName(c.getCardTypeName());
        r.setDiscountRate(c.getDiscountRate());
        r.setBalance(c.getBalance());
        r.setTotalRecharge(c.getTotalRecharge());
        r.setTotalConsume(c.getTotalConsume());
        r.setStatus(c.getStatus());
        r.setCreateTime(c.getCreateTime());
        return r;
    }
}
