package com.chodocu.backend.enums;

public enum OrderStatus {
    PENDING,    // Người mua gửi yêu cầu đặt mua / hẹn gặp -> Chờ người bán duyệt
    ACCEPTED,   // Người bán đã duyệt -> Sản phẩm chuyển sang Đang giao dịch (IN_TRANSACTION)
    COMPLETED,  // Đã giao đồ thành công -> Sản phẩm chuyển sang Đã bán (SOLD)
    REJECTED,   // Người bán từ chối
    CANCELLED   // Người mua hủy yêu cầu
}
