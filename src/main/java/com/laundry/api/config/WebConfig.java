package com.laundry.api.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * Web 配置：静态资源映射
 * 将磁盘上的照片目录映射到 /photos/** URL 路径
 *
 * 关键设计：
 * - uploadDir 可能是相对路径（如 ./uploads/photos），相对路径以启动时的 user.dir 为基准
 * - 必须在 Bean 初始化时解析成绝对路径，避免内嵌 Tomcat 请求线程内工作目录发生漂移
 * - PhotoService 与本映射必须使用同一套解析逻辑，否则文件存到 A 处，访问从 B 处拿会 404
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    @Value("${photo.upload-dir}")
    private String uploadDir;

    @Value("${photo.url-prefix}")
    private String urlPrefix;

    private File resolvedRootDir;

    @PostConstruct
    public void init() {
        File dir = new File(uploadDir);
        if (!dir.isAbsolute()) {
            dir = new File(System.getProperty("user.dir"), uploadDir).getAbsoluteFile();
        }
        resolvedRootDir = dir;
        log.info("静态资源 /photos/** 将映射到磁盘目录: {}", resolvedRootDir.getAbsolutePath());
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /photos/** 映射到磁盘真实目录（使用启动时解析好的绝对路径，避免运行期工作目录漂移）
        String diskPath = resolvedRootDir.getAbsolutePath().replace("\\", "/") + "/";
        registry.addResourceHandler(urlPrefix + "/**")
                .addResourceLocations("file:" + diskPath);
    }
}
