package com.chodocu.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeviceTokenRequest {

    @NotBlank(message = "FCM token không được để trống")
    @Size(max = 500, message = "FCM token tối đa 500 ký tự")
    private String token;

    @Size(max = 30)
    private String platform;

    @Size(max = 120)
    private String packageName;
}
