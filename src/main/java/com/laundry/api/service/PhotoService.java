package com.laundry.api.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import com.laundry.api.utils.CurrentUserUtil;
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
import java.nio.file.Files;
import java.time.Instant;

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
    @Value("${photo.orphan-retention-hours:24}") private long orphanRetentionHours;
    @Value("${photo.capacity-warning-mb:10240}") private long capacityWarningMb;
    private final JdbcTemplate jdbc;
    private final CurrentUserUtil currentUser;
    public PhotoService(JdbcTemplate jdbc, CurrentUserUtil currentUser) { this.jdbc=jdbc; this.currentUser=currentUser; }

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
        audit("UPLOAD", dateDir + "/" + filename, file.getSize(), null);
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
            long size=file.length();
            if (!file.delete()) {
                log.warn("删除照片失败: {}", file.getAbsolutePath());
            } else audit("DELETE", filename, size, null);
        }
    }

    public File readPhoto(String filename) { File file=getPhoto(filename);audit("READ",filename,file.length(),null);return file; }

    @Scheduled(cron = "${photo.cleanup-cron:0 20 4 * * *}")
    public void cleanupOrphans() {
        if (resolvedRootDir == null || !resolvedRootDir.isDirectory()) return;
        Instant cutoff=Instant.now().minusSeconds(orphanRetentionHours*3600);long bytes=0;int removed=0;
        try(var paths=Files.walk(resolvedRootDir.toPath())){
            for(var path:paths.filter(Files::isRegularFile).toList()){
                bytes+=Files.size(path);if(Files.getLastModifiedTime(path).toInstant().isAfter(cutoff))continue;
                String name=resolvedRootDir.toPath().relativize(path).toString().replace('\\','/');
                Integer refs=jdbc.queryForObject("SELECT COUNT(*) FROM laundry_order WHERE defect_photos LIKE CONCAT('%',?,'%')",Integer.class,name);
                if(refs!=null&&refs==0&&Files.deleteIfExists(path)){removed++;audit("CLEANUP",name,null,"超过保留期且无订单引用");}
            }
        }catch(Exception e){log.error("照片孤儿文件清理失败",e);}
        if(bytes>capacityWarningMb*1024*1024)log.warn("照片目录容量超过阈值: {} MB",bytes/1024/1024);
        if(removed>0)log.info("已清理 {} 个孤儿照片文件",removed);
    }

    private void audit(String action,String filename,Long size,String detail){String operator=null;try{operator=currentUser.getOperatorName();}catch(Exception ignored){}jdbc.update("INSERT INTO photo_access_audit(action,filename,file_size,operator_name,detail) VALUES(?,?,?,?,?)",action,filename,size,operator,detail);}

    private void validateFilename(String filename) {
        if (filename == null || !filename.matches("^\\d{8}/[a-f0-9]{32}\\.(jpg|png)$")) {
            throw new IllegalArgumentException("非法文件名");
        }
    }
}
