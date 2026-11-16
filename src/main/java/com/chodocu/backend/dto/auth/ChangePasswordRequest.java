package com.chodocu.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Dữ liệu đổi mật khẩu: phải nhập đúng mật khẩu hiện tại. */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại")
    private String currentPassword;

    @NotBlank(message = "Vui lòng nhập mật khẩu mới")
    @Size(min = 6, max = 72, message = "Mật khẩu mới phải có từ 6 đến 72 ký tự")
    private String newPassword;
}
