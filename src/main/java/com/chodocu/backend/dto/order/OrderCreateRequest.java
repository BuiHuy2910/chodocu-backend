package com.chodocu.backend.dto.order;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Yêu cầu đặt mua + hẹn gặp. Người mua phải tự chọn điểm hẹn và thời gian,
 * hệ thống không tự điền.
 */
@Data
public class OrderCreateRequest {

    @NotNull(message = "Mã sản phẩm không được để trống")
    private Long productId;

    /** Giá đề nghị; bỏ trống = mua đúng giá niêm yết. */
    @DecimalMin(value = "0", message = "Giá đề nghị không được âm")
    private BigDecimal offeredPrice;

    /** Một trong các điểm hẹn của trường (University.getMeetupSpots()). */
    @NotBlank(message = "Vui lòng chọn điểm hẹn")
    private String meetupLocation;

    /** Định dạng ISO "yyyy-MM-dd'T'HH:mm", ví dụ "2026-11-20T17:30". */
    @NotBlank(message = "Vui lòng chọn thời gian hẹn")
    private String meetupTime;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String buyerNote;
}
