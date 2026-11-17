package com.chodocu.backend.service;

import com.chodocu.backend.dto.DeviceTokenRequest;
import com.chodocu.backend.entity.DeviceToken;
import com.chodocu.backend.entity.User;
import com.chodocu.backend.exception.ResourceNotFoundException;
import com.chodocu.backend.repository.DeviceTokenRepository;
import com.chodocu.backend.repository.UserRepository;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Đăng ký FCM device token và gửi push notification qua Firebase Cloud Messaging (HTTP v1).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FcmPushService {

    public static final String ANDROID_CHANNEL_ID = "chodocu_default";
    private static final String APP_NAME = "chodocu-fcm";

    private final DeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;

    @Value("${app.firebase.enabled:true}")
    private boolean enabled;

    @Value("${app.firebase.credentials-path:}")
    private String credentialsPath;

    @Value("${app.firebase.credentials-json:}")
    private String credentialsJson;

    @Value("${app.firebase.project-id:chodocu-1e1be}")
    private String projectId;

    private final ExecutorService pushExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread t = new Thread(runnable, "fcm-push-worker");
        t.setDaemon(true);
        return t;
    });

    private volatile FirebaseMessaging firebaseMessaging;

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("FCM push notification đang tắt (app.firebase.enabled=false).");
            return;
        }
        try {
            GoogleCredentials credentials = loadCredentials();
            if (credentials == null) {
                log.warn("Chưa cấu hình Firebase Service Account JSON (FIREBASE_CREDENTIALS_PATH / FIREBASE_CREDENTIALS_JSON). " +
                        "API lưu FCM token vẫn hoạt động, nhưng backend chưa thể gửi push thực tế.");
                return;
            }
            FirebaseApp app = findOrCreateApp(credentials);
            firebaseMessaging = FirebaseMessaging.getInstance(app);
            log.info("Đã khởi tạo Firebase Cloud Messaging thành công (projectId={}).", projectId);
        } catch (Exception e) {
            log.error("Không thể khởi tạo Firebase Cloud Messaging: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        pushExecutor.shutdown();
    }

    @Transactional
    public void registerToken(String userEmail, DeviceTokenRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        String token = request.getToken().trim();
        String platform = StringUtils.hasText(request.getPlatform())
                ? request.getPlatform().trim().toUpperCase(Locale.ROOT)
                : "ANDROID";
        String packageName = StringUtils.hasText(request.getPackageName())
                ? request.getPackageName().trim()
                : null;

        DeviceToken entity = deviceTokenRepository.findByToken(token)
                .orElseGet(() -> DeviceToken.builder().token(token).build());
        entity.setUser(user);
        entity.setPlatform(platform);
        entity.setPackageName(packageName);
        deviceTokenRepository.save(entity);
    }

    @Transactional
    public void unregisterToken(String userEmail, String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        userRepository.findByEmail(userEmail).ifPresent(user ->
                deviceTokenRepository.deleteByUserIdAndToken(user.getId(), token.trim()));
    }

    /**
     * Gửi push sau khi transaction DB commit (hoặc ngay lập tức nếu không nằm trong transaction).
     */
    public void pushAfterCommit(Long userId, String title, String body, Map<String, String> data) {
        if (userId == null || firebaseMessaging == null) {
            return;
        }
        Map<String, String> payload = cleanData(title, body, data);
        Runnable asyncTask = () -> pushExecutor.submit(() -> sendNow(userId, title, body, payload));

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    asyncTask.run();
                }
            });
        } else {
            asyncTask.run();
        }
    }

    public boolean isReady() {
        return firebaseMessaging != null;
    }

    private void sendNow(Long userId, String title, String body, Map<String, String> data) {
        FirebaseMessaging messaging = firebaseMessaging;
        if (messaging == null) {
            return;
        }
        try {
            List<String> tokens = deviceTokenRepository.findTokensByUserId(userId);
            if (tokens.isEmpty()) {
                return;
            }
            String safeTitle = truncate(title, 120);
            String safeBody = truncate(body, 240);

            MulticastMessage message = MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .setNotification(Notification.builder()
                            .setTitle(safeTitle)
                            .setBody(safeBody)
                            .build())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setChannelId(ANDROID_CHANNEL_ID)
                                    .setSound("default")
                                    .build())
                            .build())
                    .putAllData(data)
                    .build();

            BatchResponse response = messaging.sendEachForMulticast(message);
            if (response.getFailureCount() > 0) {
                pruneInvalidTokens(tokens, response.getResponses());
            }
        } catch (Exception e) {
            log.warn("Gửi FCM push cho userId={} thất bại: {}", userId, e.getMessage());
        }
    }

    private void pruneInvalidTokens(List<String> tokens, List<SendResponse> responses) {
        List<String> invalidTokens = new ArrayList<>();
        for (int i = 0; i < responses.size() && i < tokens.size(); i++) {
            SendResponse item = responses.get(i);
            if (!item.isSuccessful() && item.getException() != null) {
                MessagingErrorCode code = item.getException().getMessagingErrorCode();
                if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                    invalidTokens.add(tokens.get(i));
                }
            }
        }
        if (!invalidTokens.isEmpty()) {
            deviceTokenRepository.deleteByTokenIn(invalidTokens);
            log.info("Đã xóa {} FCM token không còn hợp lệ.", invalidTokens.size());
        }
    }

    private FirebaseApp findOrCreateApp(GoogleCredentials credentials) {
        for (FirebaseApp existing : FirebaseApp.getApps()) {
            if (APP_NAME.equals(existing.getName())) {
                return existing;
            }
        }
        FirebaseOptions.Builder builder = FirebaseOptions.builder().setCredentials(credentials);
        if (StringUtils.hasText(projectId)) {
            builder.setProjectId(projectId.trim());
        }
        return FirebaseApp.initializeApp(builder.build(), APP_NAME);
    }

    private GoogleCredentials loadCredentials() throws IOException {
        if (StringUtils.hasText(credentialsJson)) {
            try (InputStream in = new ByteArrayInputStream(credentialsJson.trim().getBytes(StandardCharsets.UTF_8))) {
                return GoogleCredentials.fromStream(in);
            }
        }
        if (StringUtils.hasText(credentialsPath)) {
            Path path = Paths.get(credentialsPath.trim());
            if (Files.isRegularFile(path)) {
                try (InputStream in = Files.newInputStream(path)) {
                    return GoogleCredentials.fromStream(in);
                }
            }
            log.warn("Không tìm thấy file Firebase credentials tại: {}", path);
        }
        String envCreds = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (StringUtils.hasText(envCreds) && Files.isRegularFile(Paths.get(envCreds.trim()))) {
            return GoogleCredentials.getApplicationDefault();
        }
        return null;
    }

    private Map<String, String> cleanData(String title, String body, Map<String, String> extra) {
        Map<String, String> map = new LinkedHashMap<>();
        if (extra != null) {
            extra.forEach((k, v) -> {
                if (StringUtils.hasText(k) && v != null) {
                    map.put(k, v);
                }
            });
        }
        if (StringUtils.hasText(title)) {
            map.putIfAbsent("title", truncate(title, 120));
        }
        if (StringUtils.hasText(body)) {
            map.putIfAbsent("body", truncate(body, 240));
        }
        return map;
    }

    private String truncate(String text, int maxLen) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen - 1) + "…";
    }
}
