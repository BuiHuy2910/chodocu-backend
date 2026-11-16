package com.chodocu.backend.dto.admin;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Duyệt / từ chối nhiều tin đăng cùng lúc. */
@Data
public class BulkModerationRequest {

    @NotEmpty(message = "Vui lòng chọn ít nhất 1 tin đăng")
    @Size(max = 100, message = "Mỗi lần chỉ xử lý tối đa 100 tin đăng")
    private List<Long> ids;

    /** Bắt buộc khi từ chối. */
    private String reason;
}
