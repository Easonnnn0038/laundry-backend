package com.laundry.api.controller;

import com.laundry.api.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.util.concurrent.TimeUnit;

import java.util.Map;

/**
 * 照片控制器（瑕疵拍照取证）
 * 支持脚踏板触发拍照：前端监听键盘事件 → 调用摄像头 → 上传到本接口
 */
@Tag(name = "照片管理", description = "瑕疵拍照上传、删除接口")
@RestController
@RequestMapping("/api/photo")
public class PhotoController {

    @Autowired
    private com.laundry.api.service.PhotoService photoService;

    @Operation(summary = "上传照片", description = "接收 multipart 文件，保存到磁盘，返回访问URL。支持脚踏板触发的拍照上传")
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = photoService.uploadPhoto(file);
        return Result.success(result);
    }

    @Operation(summary = "删除照片", description = "根据文件名删除磁盘上的照片")
    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestParam("filename") String filename) {
        photoService.deletePhoto(filename);
        return Result.success();
    }

    @GetMapping("/file/{date}/{filename:.+}")
    public ResponseEntity<FileSystemResource> file(@PathVariable String date,
                                                    @PathVariable String filename) {
        File photo = photoService.getPhoto(date + "/" + filename);
        MediaType type = filename.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePrivate())
                .body(new FileSystemResource(photo));
    }
}
