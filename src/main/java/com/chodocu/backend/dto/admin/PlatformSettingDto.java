package com.chodocu.backend.dto.admin;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformSettingDto {
    private String key;
    private String value;
    private String description;
    private String updatedAt;
    private String updatedBy;
}
