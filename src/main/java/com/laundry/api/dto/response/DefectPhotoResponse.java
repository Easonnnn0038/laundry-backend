package com.laundry.api.dto.response;

import lombok.Data;

/**
 * 瑕疵照片响应
 */
@Data
public class DefectPhotoResponse {

    /** 照片ID */
    private String id;

    /** 文件名 */
    private String filename;

    /** 访问URL */
    private String url;

    /** 文件大小(字节) */
    private Long size;

    /** 瑕疵类型（多选，逗号分隔，如：污渍,破损,其他） */
    private String defectType;

    /** 瑕疵其他备注（defectType含"其他"时填写） */
    private String defectRemark;
}
