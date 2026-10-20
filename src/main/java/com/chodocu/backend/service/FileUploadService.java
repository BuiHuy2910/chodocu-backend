package com.chodocu.backend.service;

import com.chodocu.backend.exception.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileUploadService {

    private final Path uploadDir = Paths.get("uploads");

    public FileUploadService() {
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException ignored) {
        }
    }

    public String storeFile(MultipartFile file, String baseUrl) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File ảnh không hợp lệ");
        }
        try {
            String originalName = file.getOriginalFilename();
            String ext = (originalName != null && originalName.contains("."))
                    ? originalName.substring(originalName.lastIndexOf("."))
                    : ".jpg";
            String fileName = UUID.randomUUID() + ext;
            Path targetLocation = this.uploadDir.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return baseUrl + "/uploads/" + fileName;
        } catch (IOException ex) {
            throw new BadRequestException("Không thể lưu file ảnh: " + ex.getMessage());
        }
    }
}
