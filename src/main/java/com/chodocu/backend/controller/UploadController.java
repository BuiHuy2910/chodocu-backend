package com.chodocu.backend.controller;

import com.chodocu.backend.dto.ApiResponse;
import com.chodocu.backend.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Map;

/**
 * Upload ảnh (cần đăng nhập). Trả về {"url": "..."} để gắn vào tin đăng, avatar hoặc tin nhắn chat.
 */
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final FileUploadService fileUploadService;

    /** folder: products (mặc định) | avatars | chat */
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "products") String folder
    ) {
        String url = toAbsoluteUrl(fileUploadService.storeFile(file, folder));
        return ResponseEntity.ok(ApiResponse.ok("Tải ảnh lên thành công!", Map.of("url", url)));
    }

    /** Upload ảnh đại diện — viết tắt của folder=avatars. */
    @PostMapping("/avatar")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return uploadImage(file, "avatars");
    }

    /** Ảnh lưu local có dạng "/uploads/..." -> ghép domain backend để app hiển thị được. */
    private String toAbsoluteUrl(String url) {
        return url.startsWith("/")
                ? ServletUriComponentsBuilder.fromCurrentContextPath().path(url).toUriString()
                : url;
    }
}
