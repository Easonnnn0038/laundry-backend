package com.laundry.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laundry.api.dto.request.CustomerSaveRequest;
import com.laundry.api.dto.response.CustomerResponse;
import com.laundry.api.dto.response.MemberCardSimpleResponse;
import com.laundry.api.entity.Customer;
import com.laundry.api.entity.MemberCard;
import com.laundry.api.mapper.CustomerMapper;
import com.laundry.api.mapper.MemberCardMapper;
import com.laundry.api.service.CustomerService;
import com.laundry.api.service.MemberCardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 客户服务实现
 */
@Service
public class CustomerServiceImpl implements CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceImpl.class);

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private MemberCardMapper memberCardMapper;

    @Autowired
    private MemberCardService memberCardService;

    @Override
    public CustomerResponse searchByPhone(String phone, String storeCode) {
        if (phone == null || phone.isBlank()) return null;
        LambdaQueryWrapper<Customer> qw = new LambdaQueryWrapper<>();
        qw.eq(Customer::getPhone, phone.trim())
          .eq(Customer::getStoreCode, storeCode);
        Customer c = customerMapper.selectOne(qw);
        if (c == null) {
            log.debug("未找到客户 phone={}, storeCode={}", phone, storeCode);
            return null;
        }
        return toResponse(c);
    }

    @Override
    public CustomerResponse save(CustomerSaveRequest request, String storeCode) {
        // 手机号+门店号是唯一索引，先查
        LambdaQueryWrapper<Customer> qw = new LambdaQueryWrapper<>();
        qw.eq(Customer::getPhone, request.getPhone().trim())
          .eq(Customer::getStoreCode, storeCode);
        Customer existing = customerMapper.selectOne(qw);

        if (existing == null) {
            // 新客户：新增
            Customer c = new Customer();
            c.setName(request.getName().trim());
            c.setPhone(request.getPhone().trim());
            c.setAddress(request.getAddress());
            c.setRemark(request.getRemark());
            c.setStoreCode(storeCode);
            c.setTotalCount(0);
            c.setTotalAmount(java.math.BigDecimal.ZERO);
            c.setCreateTime(LocalDateTime.now());
            c.setUpdateTime(LocalDateTime.now());
            customerMapper.insert(c);
            log.info("新增客户 id={}, name={}, phone={}", c.getId(), c.getName(), c.getPhone());
            return toResponse(c);
        } else {
            // 老客户：更新（姓名/地址/备注更新为最新）
            boolean changed = false;
            if (request.getName() != null && !request.getName().isBlank()
                    && !request.getName().trim().equals(existing.getName())) {
                existing.setName(request.getName().trim());
                changed = true;
            }
            if (request.getAddress() != null && !request.getAddress().equals(existing.getAddress())) {
                existing.setAddress(request.getAddress());
                changed = true;
            }
            if (request.getRemark() != null && !request.getRemark().equals(existing.getRemark())) {
                existing.setRemark(request.getRemark());
                changed = true;
            }
            if (changed) {
                existing.setUpdateTime(LocalDateTime.now());
                customerMapper.updateById(existing);
                log.info("更新客户 id={}", existing.getId());
            }
            return toResponse(existing);
        }
    }

    private CustomerResponse toResponse(Customer c) {
        CustomerResponse r = new CustomerResponse();
        r.setId(c.getId());
        r.setName(c.getName());
        r.setPhone(c.getPhone());
        r.setAddress(c.getAddress());
        r.setRemark(c.getRemark());
        r.setStoreCode(c.getStoreCode());
        r.setTotalCount(c.getTotalCount());
        r.setTotalAmount(c.getTotalAmount());
        r.setCreateTime(c.getCreateTime());

        // 带出会员卡信息（每人一张正常卡）
        LambdaQueryWrapper<MemberCard> cardQw = new LambdaQueryWrapper<>();
        cardQw.eq(MemberCard::getCustomerId, c.getId())
              .eq(MemberCard::getStatus, 1)
              .last("LIMIT 1");
        MemberCard card = memberCardMapper.selectOne(cardQw);
        if (card != null) {
            MemberCardSimpleResponse cardResp = memberCardService.getById(card.getId());
            r.setMemberCard(cardResp);
        }
        return r;
    }
}
