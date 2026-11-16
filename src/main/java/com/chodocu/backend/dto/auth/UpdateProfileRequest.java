package com.chodocu.backend.dto.auth;

import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Cập nhật hồ sơ: trường nào để null thì giữ nguyên giá trị cũ.
 * Số điện thoại được kiểm tra định dạng ở AuthService.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @Size(max = 100, message = "Họ và tên tối đa 100 ký tự")
    private String fullName;

    private String phone;

    @Size(max = 255, message = "Khu vực / KTX tối đa 255 ký tự")
    private String dormArea;

    private Long universityId;

    @Size(max = 1000, message = "Đường dẫn ảnh đại diện quá dài")
    private String avatarUrl;

    @Size(max = 500, message = "Giới thiệu tối đa 500 ký tự")
    private String bio;
}
