package com.laundry.api.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.util.Iterator;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 照片服务：保存/删除瑕疵拍照图片到磁盘
 * 关键设计：在 Bean 初始化时把 uploadDir 规范化为绝对路径并创建根目录，
 * 避免内嵌 Tomcat 请求期间工作目录临时变化导致的相对路径解析错误。
 */
@Service
public class PhotoService {

    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);

    @Value("${photo.upload-dir}")
    private String uploadDir;

    @Value("${photo.url-prefix}")
    private String urlPrefix;

    /**
     * 启动时规范化后的绝对存储根目录。
     * 在 Bean 初始化时基于 user.dir（启动时的工作目录）解析一次，避免运行期相对路径解析漂移。
     */
    private File resolvedRootDir;
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final long MAX_PIXELS = 40_000_000L;

    @PostConstruct
    public void init() {
        File dir = new File(uploadDir);
        // 如果配置值不是绝对路径，则以 JVM 启动目录 (user.dir) 为基准
        if (!dir.isAbsolute()) {
            dir = new File(System.getProperty("user.dir"), uploadDir).getAbsoluteFile();
        }
        resolvedRootDir = dir;
        // 确保根目录预先创建
        if (!resolvedRootDir.exists() && !resolvedRootDir.mkdirs()) {
            log.warn("未能预先创建照片根目录，将在上传时重试：{}", resolvedRootDir.getAbsolutePath());
        } else {
            log.info("照片存储目录初始化完成: {}", resolvedRootDir.getAbsolutePath());
        }
    }

    /**
     * 上传照片到磁盘
     * 返回 { filename, url, size }
     */
    public Map<String, Object> uploadPhoto(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("照片文件为空");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("照片不能超过10MB");
        }

        String contentType = file.getContentType();
        if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
            throw new IllegalArgumentException("仅支持 JPG 和 PNG 图片");
        }

        String ext;
        try (ImageInputStream input = ImageIO.createImageInputStream(file.getInputStream())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("文件不是有效图片");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase();
                if (format.equals("jpg") || format.equals("jpeg")) ext = ".jpg";
                else if (format.equals("png")) ext = ".png";
                else throw new IllegalArgumentException("仅支持 JPG 和 PNG 图片");
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > MAX_PIXELS) throw new IllegalArgumentException("图片尺寸过大");
                reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("无法读取图片文件");
        }

        // 按日期分目录：uploads/photos/20260812/xxx.jpg
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        File dir = new File(resolvedRootDir, dateDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("创建照片目录失败: " + dir.getAbsolutePath());
        }

        // 生成唯一文件名
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;

        // 保存文件
        File dest = new File(dir, filename);
        try {
            file.transferTo(dest);
        } catch (IOException e) {
            log.error("保存照片失败, 目标路径: {}", dest.getAbsolutePath(), e);
            throw new RuntimeException("保存照片失败: " + e.getMessage() + ", 目标: " + dest.getAbsolutePath());
        }

        // 返回访问URL和元信息
        String url = urlPrefix + "/" + dateDir + "/" + filename;
        Map<String, Object> result = new HashMap<>();
        result.put("filename", dateDir + "/" + filename);
        result.put("url", url);
        result.put("size", file.getSize());
        log.info("照片上传成功: {} ({}KB) -> {}", filename, file.getSize() / 1024, dest.getAbsolutePath());
        return result;
    }

    public File getPhoto(String filename) {
        validateFilename(filename);
        try {
            File file = new File(resolvedRootDir, filename).getCanonicalFile();
            if (!file.toPath().startsWith(resolvedRootDir.getCanonicalFile().toPath()) || !file.isFile()) {
                throw new IllegalArgumentException("照片不存在");
            }
            return file;
        } catch (IOException e) {
            throw new IllegalArgumentException("照片路径无效");
        }
    }

    /**
     * 删除磁盘上的照片
     */
    public void deletePhoto(String filename) {
        if (filename == null || filename.isEmpty()) return;
        File file = getPhoto(filename);
        if (file.exists()) {
            if (!file.delete()) {
                log.warn("删除照片失败: {}", file.getAbsolutePath());
            }
        }
    }

    private void validateFilename(String filename) {
        if (filename == null || !filename.matches("^\\d{8}/[a-f0-9]{32}\\.(jpg|png)$")) {
            throw new IllegalArgumentException("非法文件名");
        }
    }
}
