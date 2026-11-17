package com.chodocu.backend.service;

import com.chodocu.backend.dto.NotificationResponse;
import com.chodocu.backend.entity.Notification;
import com.chodocu.backend.entity.User;
import com.chodocu.backend.exception.ResourceNotFoundException;
import com.chodocu.backend.repository.NotificationRepository;
import com.chodocu.backend.repository.UserRepository;
import com.chodocu.backend.util.DisplayFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final FcmPushService fcmPushService;

    @Transactional
    public void createNotification(User user, String type, String title, String message, Long referenceId) {
        notificationRepository.save(Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .message(message)
                .referenceId(referenceId)
                .build());

        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", type != null ? type : "GENERAL");
        if (referenceId != null) {
            data.put("referenceId", String.valueOf(referenceId));
        }
        fcmPushService.pushAfterCommit(user.getId(), title, message, data);
    }

    public List<NotificationResponse> getMyNotifications(String email) {
        User user = findUser(email);
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public long getUnreadCount(String email) {
        return notificationRepository.countByUserIdAndIsReadFalse(findUser(email).getId());
    }

    @Transactional
    public void markAllRead(String email) {
        List<Notification> unread =
                notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(findUser(email).getId());
        unread.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(unread);
    }

    /** Đánh dấu một thông báo đã đọc — chỉ chủ của thông báo mới làm được. */
    @Transactional
    public void markRead(Long id, String email) {
        User user = findUser(email);
        notificationRepository.findById(id)
                .filter(n -> n.getUser() != null && n.getUser().getId().equals(user.getId()))
                .ifPresent(n -> {
                    n.setIsRead(true);
                    notificationRepository.save(n);
                });
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
    }

    private NotificationResponse toDto(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .referenceId(n.getReferenceId())
                .isRead(n.getIsRead())
                .createdAt(DisplayFormat.dateTime(n.getCreatedAt()))
                .createdAtIso(DisplayFormat.iso(n.getCreatedAt()))
                .build();
    }
}
