package com.laundry.api.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 条码生成工具类
 * 生成 Code128 格式的一维条码，返回 PNG 图片的 Base64 字符串
 * 用于打印标签（12cm×2cm条码标签）和80mm收衣凭证
 */
public class BarcodeUtil {

    private static final Logger log = LoggerFactory.getLogger(BarcodeUtil.class);

    /**
     * 生成 Code128 条码图片（Base64 PNG）- 标签纸用
     * 尺寸：宽 400px × 高 80px（对应约12cm×2cm标签）
     *
     * @param code 条码数字（12位）
     * @return Base64编码的PNG图片，不含data:image前缀
     */
    public static String generateCode128Base64(String code) {
        return generateCode128Base64(code, 400, 80);
    }

    /**
     * 生成 Code128 条码图片（Base64 PNG）- 自定义尺寸
     *
     * @param code   条码内容
     * @param width  宽度（像素）
     * @param height 高度（像素）
     * @return Base64编码的PNG图片
     */
    public static String generateCode128Base64(String code, int width, int height) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1); // 最小边距

            Code128Writer writer = new Code128Writer();
            BitMatrix bitMatrix = writer.encode(code, BarcodeFormat.CODE_128, width, height, hints);

            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", baos);

            byte[] imageBytes = baos.toByteArray();
            return Base64.getEncoder().encodeToString(imageBytes);
        } catch (Exception e) {
            log.error("生成条码失败 code={}", code, e);
            throw new RuntimeException("条码生成失败：" + e.getMessage(), e);
        }
    }

    /**
     * 生成带 data:image/png;base64, 前缀的完整字符串
     * 可直接用于前端 <img src="xxx"> 显示
     */
    public static String generateCode128DataUri(String code) {
        return "data:image/png;base64," + generateCode128Base64(code);
    }

    /**
     * 生成带 data:image/png;base64, 前缀的完整字符串（自定义尺寸）
     */
    public static String generateCode128DataUri(String code, int width, int height) {
        return "data:image/png;base64," + generateCode128Base64(code, width, height);
    }
}
