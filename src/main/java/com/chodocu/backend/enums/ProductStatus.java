package com.chodocu.backend.enums;

public enum ProductStatus {
    PENDING,         // Chờ Admin kiểm duyệt
    APPROVED,        // Đã duyệt - Đang hiển thị bán
    IN_TRANSACTION,  // Đang giao dịch / Đã chốt hẹn gặp (ẩn khỏi feed tìm kiếm chung)
    SOLD,            // Đã bán (ẩn khỏi feed tìm kiếm chung)
    REJECTED,        // Bị Admin từ chối
    HIDDEN           // Người bán tự ẩn
}
