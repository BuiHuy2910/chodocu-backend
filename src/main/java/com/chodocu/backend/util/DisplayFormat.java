package com.chodocu.backend.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Các hàm định dạng dùng chung cho toàn backend:
 * ngày giờ ISO cho app, ngày giờ dễ đọc, tiền VNĐ và giờ hẹn gặp.
 *
 * Quy ước thời gian:
 * - Thời điểm lưu trong DB (createdAt, updatedAt...) là giờ UTC.
 * - Giờ hẹn gặp (meetupTime) là giờ Việt Nam do người dùng chọn, dạng "2026-11-20T17:30".
 */
public final class DisplayFormat {

    /** Múi giờ hiển thị cho người dùng. */
    public static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter SHORT_TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM");
    private static final DateTimeFormatter MEETUP_TIME = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");
    private static final Locale VIETNAM = Locale.forLanguageTag("vi-VN");

    private DisplayFormat() {
    }

    /** Thời điểm hiện tại theo UTC — dùng khi lưu vào DB. */
    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    /** Giờ hiện tại ở Việt Nam — dùng để so với giờ hẹn gặp người dùng chọn. */
    public static LocalDateTime nowInVietnam() {
        return LocalDateTime.now(VIETNAM_ZONE);
    }

    /** Ngày giờ chuẩn ISO kèm "Z" (giờ UTC), ví dụ "2026-11-16T03:20:30Z" — app tự đổi sang giờ máy. */
    public static String iso(LocalDateTime utcTime) {
        if (utcTime == null) return null;
        return utcTime.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z";
    }

    /** Ngày giờ dễ đọc theo giờ Việt Nam, ví dụ "16/11/2026 10:20". */
    public static String dateTime(LocalDateTime utcTime) {
        return utcTime == null ? "" : toVietnam(utcTime).format(DATE_TIME);
    }

    /** Giờ ngắn theo giờ Việt Nam, ví dụ "10:20 16/11" (dùng trong chat). */
    public static String shortTime(LocalDateTime utcTime) {
        return utcTime == null ? "" : toVietnam(utcTime).format(SHORT_TIME);
    }

    /** Tiền VNĐ, ví dụ 120000 -> "120.000 đ" (giống app và hàm _price_text trong SQL). */
    public static String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(VIETNAM);
        format.setMaximumFractionDigits(0);
        return format.format(amount == null ? BigDecimal.ZERO : amount) + " đ";
    }

    /** Giờ hẹn "2026-11-20T17:30" -> "17:30 ngày 20/11/2026". Dữ liệu cũ không theo ISO thì giữ nguyên. */
    public static String meetupTime(String isoTime) {
        if (isoTime == null || isoTime.isBlank()) return "";
        try {
            return LocalDateTime.parse(isoTime).format(MEETUP_TIME);
        } catch (DateTimeParseException e) {
            return isoTime;
        }
    }

    private static LocalDateTime toVietnam(LocalDateTime utcTime) {
        return utcTime.atOffset(ZoneOffset.UTC).atZoneSameInstant(VIETNAM_ZONE).toLocalDateTime();
    }
}
