package com.chodocu.backend.controller;

import com.chodocu.backend.dto.ApiResponse;
import com.chodocu.backend.service.FileUploadService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final FileUploadService fileUploadService;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "products") String folder,
            HttpServletRequest request
    ) {
        String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        String fileUrl = fileUploadService.storeFile(file, baseUrl, folder);
        // Nếu là đường dẫn local (fallback), ghép baseUrl để app hiển thị được
        if (fileUrl.startsWith("/")) {
            fileUrl = baseUrl + fileUrl;
        }
        return ResponseEntity.ok(ApiResponse.ok("Tải ảnh lên thành công!", Map.of("url", fileUrl)));
    }

    /** Upload ảnh đại diện — dùng ngắn gọn cho app mobile. */
    @PostMapping("/avatar")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request
    ) {
        return uploadImage(file, "avatars", request);
    }
}
