package com.laundry.api.service;

import com.laundry.api.dto.request.CustomerSaveRequest;
import com.laundry.api.dto.response.CustomerResponse;

/**
 * 客户服务接口
 */
public interface CustomerService {

    /**
     * 根据手机号查询客户（自动带出会员卡信息）
     * 收衣页输入手机号时调用
     *
     * @param phone      手机号
     * @param storeCode  门店编号
     * @return 客户信息（找不到返回null）
     */
    CustomerResponse searchByPhone(String phone, String storeCode);

    /**
     * 保存/更新客户信息
     * 新客户：新增
     * 老客户：根据手机号+门店号更新信息
     *
     * @return 保存后的客户信息
     */
    CustomerResponse save(CustomerSaveRequest request, String storeCode);
}
