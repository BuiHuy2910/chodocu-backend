package com.chodocu.backend.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReportRequest {
    private Long productId;
    private String reason;
    private String detail;
}
