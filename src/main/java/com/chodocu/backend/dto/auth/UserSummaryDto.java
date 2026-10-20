package com.chodocu.backend.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryDto {
    private Long id;
    private String email;
    private String fullName;
    private String studentId;
    private String phone;
    private String avatarUrl;
    private Long universityId;
    private String universityName;
    private String universityShortName;
    private String dormArea;
    private String role;
    private Boolean verifiedStudent;
    private Boolean active;
    private Double rating;
    private Integer completedDeals;
    private String createdAt;
}
