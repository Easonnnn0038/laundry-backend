package com.laundry.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 提交收衣订单请求（核心接口）
 * 包含：客户信息 + 衣物明细列表 + 会员卡/办卡信息 + 支付信息
 */
@Data
@Schema(description = "提交收衣订单请求")
public class ReceiveOrderRequest {

    @NotBlank(message = "请求号不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_-]{16,64}$", message = "请求号格式不正确")
    private String requestId;

    @Schema(description = "管理员改价原因；任一衣物价格偏离价目表时必填")
    private String priceOverrideReason;

    // ========== 客户信息 ==========
    @Schema(description = "客户ID（已有客户时传入；新客户不传）", example = "1")
    private Long customerId;

    @Schema(description = "客户姓名（新客户必填，老客户会校验但可修改）", required = true, example = "李女士")
    @NotBlank(message = "客户姓名不能为空")
    private String customerName;

    @Schema(description = "联系电话（必填，自动带出已有客户）", required = true, example = "13999507012")
    @NotBlank(message = "联系电话不能为空")
    private String customerPhone;

    @Schema(description = "客户地址（选填）")
    private String customerAddress;

    @Schema(description = "客户备注（选填）")
    private String customerRemark;

    // ========== 办卡信息（收衣时同时办卡才传，newCardFlag=1） ==========
    @Schema(description = "是否同时办卡：0=否 1=是（办卡弹窗确认后为1）", example = "0")
    private Integer newCardFlag = 0;

    @Schema(description = "新办卡类型ID（member_card_type表：1=8.8折卡300元，2=6.8折卡500元）", example = "1")
    private Long newCardTypeId;

    @Schema(description = "新办卡支付方式：CASH/WECHAT/ALIPAY（办卡金额从这里付，不从会员卡扣）", example = "CASH")
    private String newCardPayMethod;

    // ========== 充值信息（已有卡追加充值，rechargeFlag=1） ==========
    @Schema(description = "是否同时充值（已有卡追加充值）：0=否 1=是", example = "0")
    private Integer rechargeFlag = 0;

    @Schema(description = "充值卡类型ID（选择充值金额档位）", example = "1")
    private Long rechargeCardTypeId;

    @Schema(description = "充值支付方式：CASH/WECHAT/ALIPAY", example = "CASH")
    private String rechargePayMethod;

    // ========== 会员卡信息（已有会员卡使用） ==========
    @Schema(description = "使用的会员卡ID（有会员卡时传入；办卡后系统会关联）", example = "1")
    private Long memberCardId;

    // ========== 衣物明细 ==========
    @Schema(description = "衣物明细列表（至少1件）", required = true)
    @NotEmpty(message = "衣物列表不能为空")
    @Valid
    private List<ReceiveOrderItemRequest> items;

    // ========== 支付信息 ==========
    @Schema(description = "支付方式：CASH现金 / WECHAT微信 / ALIPAY支付宝 / MEMBER_CARD会员卡 / MIXED组合", required = true, example = "MEMBER_CARD")
    @NotBlank(message = "支付方式不能为空")
    private String paymentMethod;

    @Schema(description = "补差支付方式（paymentMethod=MIXED时必填）：CASH/WECHAT/ALIPAY", example = "WECHAT")
    private String extraMethod;

    @Schema(description = "实收金额（操作员实际收到的钱，默认=应收总额，可手动修改留欠款）", required = true, example = "52.80")
    @NotNull(message = "实收金额不能为空")
    private BigDecimal totalPaid;

    // ========== 加急 ==========
    @Schema(description = "是否加急：0=否 1=是（加急订单整体加价20%）", example = "0")
    private Integer urgentFlag = 0;

    // ========== 瑕疵拍照 ==========
    @Schema(description = "瑕疵拍照列表（每项包含图片base64数据）")
    private List<DefectPhotoItem> defectPhotos;

    @Data
    public static class DefectPhotoItem {
        @Schema(description = "照片ID")
        private String id;
        @Schema(description = "文件名（含日期子目录，如 20260812/xxx.jpg）")
        private String filename;
        @Schema(description = "访问URL路径（如 /photos/20260812/xxx.jpg）")
        private String url;
        @Schema(description = "文件大小(字节)")
        private Long size;
        @Schema(description = "瑕疵类型（多选，逗号分隔，如：污渍,破损,其他）")
        private String defectType;
        @Schema(description = "瑕疵其他备注（defectType含\"其他\"时填写）")
        private String defectRemark;
    }

    // ========== 订单备注 ==========
    @Schema(description = "订单整体备注（选填）")
    private String remark;
}
