package com.laundry.api.service;

import com.laundry.api.dto.request.MemberCardCreateRequest;
import com.laundry.api.dto.request.MemberCardRechargeRequest;
import com.laundry.api.dto.response.MemberCardSimpleResponse;
import com.laundry.api.dto.response.MemberCardTypeResponse;

import java.util.List;

/**
 * 会员卡服务接口
 */
public interface MemberCardService {

    /**
     * 查询会员卡类型列表（办卡弹窗选择用）
     */
    List<MemberCardTypeResponse> listCardTypes();

    /**
     * 根据客户ID查询会员卡（只返回状态正常的卡，每人一张卡）
     */
    MemberCardSimpleResponse getByCustomerId(Long customerId);

    /**
     * 根据ID查询会员卡详情
     */
    MemberCardSimpleResponse getById(Long cardId);

    /**
     * 办理新会员卡（办卡弹窗用或收衣时同时办卡用）
     * 办卡的同时记录充值记录（充值金额=卡类型amount）
     *
     * @param request    办卡请求
     * @param storeCode  门店号
     * @param operatorId 操作人ID
     * @param operatorName 操作人姓名
     * @return 新会员卡详情
     */
    MemberCardSimpleResponse createCard(MemberCardCreateRequest request, String storeCode,
                                        Long operatorId, String operatorName);

    /**
     * 会员卡充值
     *
     * @param request     充值请求
     * @param operatorId  操作人ID
     * @param operatorName 操作人姓名
     * @return 充值后的会员卡详情
     */
    MemberCardSimpleResponse recharge(MemberCardRechargeRequest request,
                                       Long operatorId, String operatorName);
}
