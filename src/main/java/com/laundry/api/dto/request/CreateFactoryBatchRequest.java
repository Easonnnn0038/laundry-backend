package com.laundry.api.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CreateFactoryBatchRequest {
    @NotEmpty(message = "请选择至少一个订单")
    private List<Long> orderIds;

    private String remark;
}

