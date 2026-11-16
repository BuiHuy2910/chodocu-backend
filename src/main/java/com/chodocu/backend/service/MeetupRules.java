package com.chodocu.backend.service;

import com.chodocu.backend.entity.University;
import com.chodocu.backend.exception.BadRequestException;
import com.chodocu.backend.util.DisplayFormat;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * Luật hẹn gặp dùng chung cho đặt mua (OrderService) và tin nhắn hẹn gặp (ChatService).
 * Khớp với hàm _check_meetup_time trong supabase_direct_setup.sql:
 * - Điểm hẹn do người dùng TỰ CHỌN trong danh sách điểm giao dịch chuẩn của trường.
 * - Giờ hẹn là giờ Việt Nam dạng "2026-11-20T17:30", sau thời điểm hiện tại và trong vòng 30 ngày.
 */
final class MeetupRules {

    /** Chỉ được hẹn trong vòng 30 ngày tới. */
    static final int MAX_DAYS_AHEAD = 30;

    private static final Pattern TIME_FORMAT = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}");

    private MeetupRules() {
    }

    /** Trả về điểm hẹn đã bỏ khoảng trắng thừa; báo lỗi nếu không thuộc danh sách điểm hẹn của trường. */
    static String requireSpot(University university, String location) {
        String spot = location == null ? "" : location.trim();
        if (university == null || !university.hasMeetupSpot(spot)) {
            throw new BadRequestException("Vui lòng chọn điểm hẹn trong danh sách điểm hẹn của trường.");
        }
        return spot;
    }

    /** Trả về giờ hẹn đã bỏ khoảng trắng thừa; báo lỗi nếu sai định dạng, đã qua hoặc xa hơn 30 ngày. */
    static String requireTime(String time) {
        String value = time == null ? "" : time.trim();
        if (!TIME_FORMAT.matcher(value).matches()) {
            throw new BadRequestException("Vui lòng chọn ngày và giờ hẹn gặp.");
        }

        LocalDateTime meetupAt;
        try {
            meetupAt = LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Ngày giờ hẹn không hợp lệ.");
        }

        LocalDateTime now = DisplayFormat.nowInVietnam();
        if (!meetupAt.isAfter(now)) {
            throw new BadRequestException("Giờ hẹn phải sau thời điểm hiện tại.");
        }
        if (meetupAt.isAfter(now.plusDays(MAX_DAYS_AHEAD))) {
            throw new BadRequestException("Chỉ được hẹn trong vòng " + MAX_DAYS_AHEAD + " ngày tới.");
        }
        return value;
    }
}
