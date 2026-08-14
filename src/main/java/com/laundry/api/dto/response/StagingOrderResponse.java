package com.laundry.api.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 暂存订单列表响应
 */
@Data
public class StagingOrderResponse {

    /** 订单ID */
    private Long id;

    /** 订单编号 */
    private String orderNo;

    /** 收衣时间 */
    private LocalDateTime receiveTime;

    /** 客户姓名 */
    private String customerName;

    /** 客户电话 */
    private String customerPhone;

    /** 订单状态：RECEIVED/SENT_TO_FACTORY/BACK_TO_STORE */
    private String status;

    /** 状态中文名 */
    private String statusLabel;

    /** 门店编号 */
    private String storeCode;

    /** 门店名称 */
    private String storeName;

    /** 衣物总件数 */
    private Integer totalCount;

    /** 货架位置汇总（所有衣物的货架号，逗号分隔） */
    private List<String> shelfCodes;

    /** 是否有瑕疵照片 */
    private Boolean hasDefectPhotos;
}
