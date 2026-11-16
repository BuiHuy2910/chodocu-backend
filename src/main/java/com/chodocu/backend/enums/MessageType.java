package com.chodocu.backend.enums;

/**
 * Các loại tin nhắn trong chat mua bán.
 * Cột message_type trong DB vẫn lưu dạng chuỗi (để tương thích dữ liệu cũ),
 * enum này dùng để kiểm tra dữ liệu và tránh gõ sai chuỗi.
 */
public enum MessageType {
    TEXT,     // Tin nhắn chữ
    IMAGE,    // Ảnh (có thể kèm chú thích)
    OFFER,    // Trả giá
    MEETUP,   // Đề nghị hẹn gặp (tự tạo khi đặt mua)
    SYSTEM;   // Thông báo tự động của hệ thống (chấp nhận, hủy đơn...)

    /** Đổi chuỗi từ app sang enum; trả null nếu không hợp lệ. */
    public static MessageType fromString(String value) {
        if (value == null || value.isBlank()) {
            return TEXT;
        }
        try {
            return MessageType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
