package com.laundry.api.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderListResponse {
    private Long id;
    private String orderNo;
    private String orderType;
    private String orderSource;
    private String customerName;
    private String customerPhone;
    private Integer totalCount;
    private String status;
    private String statusLabel;
    private BigDecimal totalReceivable;
    private BigDecimal totalPaid;
    private LocalDateTime receiveTime;
}
