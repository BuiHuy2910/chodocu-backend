package com.chodocu.backend.dto.auth;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    private String fullName;
    private String phone;
    private String dormArea;
    private Long universityId;
    private String avatarUrl;
    private String bio;
}
