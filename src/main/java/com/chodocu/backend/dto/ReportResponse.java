package com.chodocu.backend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportResponse {
    private Long id;
    private String reporterName;
    private String productTitle;
    private String reason;
    private String detail;
    private String status;
    private String adminNote;
    private String createdAt;
}
