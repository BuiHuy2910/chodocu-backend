package com.chodocu.backend.service;

import com.chodocu.backend.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lưu ảnh lên Supabase Storage.
 *
 * Cấu hình (application.properties):
 *   app.supabase.url=https://<project>.supabase.co
 *   app.supabase.service-key=<service_role key>   (đặt qua biến môi trường SUPABASE_SERVICE_KEY)
 *   app.supabase.storage-bucket=passuni
 *
 * Nếu chưa cấu hình service-key (hoặc Supabase lỗi), ảnh được lưu vào thư mục ./uploads
 * để môi trường dev vẫn chạy được.
 */
@Service
@Slf4j
public class FileUploadService {

    /** Thư mục được phép: ảnh sản phẩm, ảnh đại diện, ảnh gửi trong chat. */
    private static final Set<String> ALLOWED_FOLDERS = Set.of("products", "avatars", "chat");

    /** Loại ảnh được phép -> đuôi file khi lưu (không dùng đuôi file người dùng gửi lên). */
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/jpg", ".jpg", // một số app Android gửi "image/jpg"
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif"
    );

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
        } catch (IOException e) {
            log.warn("Không tạo được thư mục uploads: {}", e.getMessage());
        }
    }

    /**
     * Lưu ảnh và trả về URL công khai.
     * Khi lưu local, kết quả là đường dẫn tương đối "/uploads/..." (controller ghép thêm domain).
     *
     * @param folder "products", "avatars" hoặc "chat"
     */
    public String storeFile(MultipartFile file, String folder) {
        String extension = validate(file, folder);
        String objectPath = folder + "/" + UUID.randomUUID() + extension;

        if (isSupabaseConfigured()) {
            try {
                return uploadToSupabase(file, objectPath);
            } catch (Exception e) {
                // Supabase lỗi thì vẫn lưu local để người dùng không mất ảnh
                log.warn("Upload Supabase thất bại, chuyển sang lưu local: {}", e.getMessage());
            }
        }
        return storeLocal(file, objectPath);
    }

    /** Kiểm tra thư mục và file, trả về đuôi file theo đúng loại ảnh. */
    private String validate(MultipartFile file, String folder) {
        if (folder == null || !ALLOWED_FOLDERS.contains(folder)) {
            throw new BadRequestException("Thư mục upload không hợp lệ");
        }
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Vui lòng chọn ảnh để tải lên");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("Ảnh vượt quá dung lượng cho phép (10MB)");
        }
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        if (contentType.contains(";")) {
            contentType = contentType.split(";")[0].trim();
        }
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
            if (filename.endsWith(".png")) extension = ".png";
            else if (filename.endsWith(".webp")) extension = ".webp";
            else if (filename.endsWith(".gif")) extension = ".gif";
            else extension = ".jpg";
        }
        return extension;
    }

    private boolean isSupabaseConfigured() {
        return supabaseUrl != null && !supabaseUrl.isBlank()
                && serviceKey != null && !serviceKey.isBlank();
    }

    private String uploadToSupabase(MultipartFile file, String objectPath) throws IOException, InterruptedException {
        String baseUrl = stripTrailingSlash(supabaseUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/storage/v1/object/" + bucket + "/" + objectPath))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + serviceKey)
                .header("apikey", serviceKey)
                .header("Content-Type", file.getContentType())
                .header("x-upsert", "true")
                .POST(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Supabase trả về " + response.statusCode() + ": " + response.body());
        }
        return baseUrl + "/storage/v1/object/public/" + bucket + "/" + objectPath;
    }

    private String storeLocal(MultipartFile file, String objectPath) {
        try {
            Path target = uploadDir.resolve(objectPath);
            Files.createDirectories(target.getParent());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/" + objectPath;
        } catch (IOException e) {
            log.error("Không thể lưu ảnh local", e);
            throw new BadRequestException("Không thể lưu ảnh, vui lòng thử lại");
        }
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
