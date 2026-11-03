package com.chodocu.backend.service;

import com.chodocu.backend.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.UUID;

/**
 * Lưu ảnh lên Supabase Storage.
 *
 * Cấu hình (application.properties):
 *   app.supabase.url=https://<project>.supabase.co
 *   app.supabase.service-key=<service_role key>   (đặt qua biến môi trường SUPABASE_SERVICE_KEY)
 *   app.supabase.storage-bucket=passuni
 *
 * Nếu chưa cấu hình service-key, hệ thống tự fallback lưu vào thư mục ./uploads
 * để dev vẫn chạy được (giống hành vi cũ).
 */
@Service
@Slf4j
public class FileUploadService {

    private static final String ALLOWED_TYPES = "image/jpeg,image/png,image/webp,image/gif";
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private final Path uploadDir = Paths.get("uploads");

    @Value("${app.supabase.url:}")
    private String supabaseUrl;

    @Value("${app.supabase.service-key:}")
    private String serviceKey;

    @Value("${app.supabase.storage-bucket:passuni}")
    private String bucket;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public FileUploadService() {
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException ignored) {
        }
    }

    /**
     * Lưu file và trả về URL công khai.
     *
     * @param file    file ảnh từ multipart
     * @param baseUrl baseUrl của backend, dùng khi fallback local
     * @param folder  thư mục logic: "avatars", "products"...
     */
    public String storeFile(MultipartFile file, String baseUrl, String folder) {
        validate(file);

        String ext = extensionOf(file.getOriginalFilename());
        String objectPath = folder + "/" + UUID.randomUUID() + ext;

        if (isSupabaseConfigured()) {
            try {
                return uploadToSupabase(file, objectPath);
            } catch (Exception e) {
                log.warn("Upload Supabase thất bại, chuyển sang lưu local: {}", e.getMessage());
            }
        }
        return storeLocal(file, ext);
    }

    /** Giữ tương thích với code cũ. */
    public String storeFile(MultipartFile file, String baseUrl) {
        return storeFile(file, baseUrl, "uploads");
    }

    private boolean isSupabaseConfigured() {
        return supabaseUrl != null && !supabaseUrl.isBlank()
                && serviceKey != null && !serviceKey.isBlank();
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File ảnh không hợp lệ");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("Ảnh vượt quá 10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException("Chỉ chấp nhận ảnh JPEG, PNG, WEBP hoặc GIF");
        }
    }

    private String extensionOf(String originalName) {
        if (originalName == null || !originalName.contains(".")) return ".jpg";
        String ext = originalName.substring(originalName.lastIndexOf(".")).toLowerCase();
        return ext.length() > 6 ? ".jpg" : ext;
    }

    private String uploadToSupabase(MultipartFile file, String objectPath) throws IOException, InterruptedException {
        String endpoint = String.format("%s/storage/v1/object/%s/%s",
                stripTrailingSlash(supabaseUrl),
                bucket,
                encodePath(objectPath));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + serviceKey)
                .header("apikey", serviceKey)
                .header("Content-Type", file.getContentType() != null ? file.getContentType() : "image/jpeg")
                .header("x-upsert", "true")
                .POST(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Supabase trả về " + response.statusCode() + ": " + response.body());
        }

        return String.format("%s/storage/v1/object/public/%s/%s",
                stripTrailingSlash(supabaseUrl), bucket, encodePath(objectPath));
    }

    private String storeLocal(MultipartFile file, String ext) {
        try {
            String fileName = UUID.randomUUID() + ext;
            Path targetLocation = this.uploadDir.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            // Trả về path tương đối; controller sẽ ghép baseUrl
            return "/uploads/" + fileName;
        } catch (IOException ex) {
            throw new BadRequestException("Không thể lưu file ảnh: " + ex.getMessage());
        }
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String encodePath(String path) {
        return java.util.Arrays.stream(path.split("/"))
                .map(seg -> URLEncoder.encode(seg, StandardCharsets.UTF_8).replace("+", "%20"))
                .reduce((a, b) -> a + "/" + b)
                .orElse(path);
    }
}

