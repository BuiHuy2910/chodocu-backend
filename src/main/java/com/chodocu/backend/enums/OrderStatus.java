package com.chodocu.backend.enums;

/**
 * Vòng đời đơn hàng trên sàn PassUni.
 *
 * PENDING   : Người mua gửi yêu cầu đặt mua / hẹn gặp → chờ người bán duyệt
 * ACCEPTED  : Người bán đã duyệt → sản phẩm chuyển IN_TRANSACTION
 * COMPLETED : Giao dịch hoàn tất → sản phẩm chuyển SOLD, chốt phí sàn
 * DISPUTED  : Một bên báo tranh chấp → admin xử lý
 * REJECTED  : Người bán từ chối
 * CANCELLED : Người mua hủy (hoặc admin hủy)
 */
public enum OrderStatus {
    PENDING,
    ACCEPTED,
    COMPLETED,
    DISPUTED,
    REJECTED,
    CANCELLED;

    public boolean isFinal() {
        return this == COMPLETED || this == REJECTED || this == CANCELLED;
    }
}

