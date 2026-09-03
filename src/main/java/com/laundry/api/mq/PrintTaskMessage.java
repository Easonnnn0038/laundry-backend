package com.laundry.api.mq;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class PrintTaskMessage implements Serializable {

    private String orderNo;
    private String customerName;
    private String customerPhone;
    private String storeCode;
    private String storeName;
    private List<ItemPrintInfo> items;
    private String createTime;

    @Data
    public static class ItemPrintInfo implements Serializable {
        private String barcode;
        private String categoryName;
        private Integer quantity;
        private String color;
    }
}
