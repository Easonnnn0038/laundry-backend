package com.laundry.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 客户保存请求
 */
@Data
@Schema(description = "客户保存请求")
public class CustomerSaveRequest {

    @Schema(description = "客户姓名", required = true, example = "李女士")
    @NotBlank(message = "客户姓名不能为空")
    private String name;

    @Schema(description = "联系电话", required = true, example = "13999507012")
    @NotBlank(message = "联系电话不能为空")
    private String phone;

    @Schema(description = "客户地址", example = "金方世纪城民磬路108号")
    private String address;

    @Schema(description = "备注信息")
    private String remark;
}
