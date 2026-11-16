package com.chodocu.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Dữ liệu đăng ký tài khoản.
 * Không yêu cầu email sinh viên: chỉ cần email hợp lệ, số điện thoại và trường / khu vực.
 */
@Data
public class RegisterRequest {

    /** Số điện thoại Việt Nam: bắt đầu bằng 0 hoặc +84, theo sau 9-10 chữ số. */
    public static final String PHONE_REGEX = "^(0|\\+84)[0-9]{9,10}$";

    @NotBlank(message = "Họ và tên không được để trống")
    @Size(max = 100, message = "Họ và tên tối đa 100 ký tự")
    private String fullName;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Định dạng email không hợp lệ")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, max = 72, message = "Mật khẩu phải có từ 6 đến 72 ký tự")
    private String password;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = PHONE_REGEX, message = "Số điện thoại không hợp lệ")
    private String phone;

    @NotNull(message = "Vui lòng chọn trường / khu vực")
    private Long universityId;
}
