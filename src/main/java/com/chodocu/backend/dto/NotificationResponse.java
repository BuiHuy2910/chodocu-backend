package com.chodocu.backend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {
    private Long id;
    private String type;
    private String title;
    private String message;
    private Long referenceId;
    private Boolean isRead;
    /** Ngày giờ dễ đọc theo giờ Việt Nam, ví dụ "16/11/2026 10:20". */
    private String createdAt;
    /** Thời điểm tạo dạng ISO giờ UTC, ví dụ "2026-11-16T03:20:30Z" (app dùng trường này). */
    private String createdAtIso;
}
