package com.chodocu.backend.service;

import com.chodocu.backend.dto.admin.DashboardStatsResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cache ngắn hạn trong RAM (15 giây) cho số liệu tổng quan và bộ lọc hàng chờ duyệt.
 * Mọi thao tác ghi (đăng/sửa/duyệt/ẩn/xóa tin, tạo/cập nhật đơn, xử lý báo cáo...)
 * đều gọi {@link #invalidate()} để dữ liệu luôn mới ngay lập tức, đồng thời tránh
 * việc VPS phải vòng sang Tokyo Supabase nhiều lần liên tiếp khi tải trang.
 */
@Component
public class AdminStatsCache {

    private static final long TTL_MS = 15_000L;

    private volatile Entry<DashboardStatsResponse> adminStats;
    private volatile Entry<DashboardStatsResponse> modStats;
    private volatile Entry<PendingFilterMeta> pendingMeta;

    public DashboardStatsResponse getStats(boolean includeFinancial) {
        Entry<DashboardStatsResponse> e = includeFinancial ? adminStats : modStats;
        return (e != null && e.expiresAt > System.currentTimeMillis()) ? e.value : null;
    }

    public void putStats(boolean includeFinancial, DashboardStatsResponse value) {
        Entry<DashboardStatsResponse> e = new Entry<>(value, System.currentTimeMillis() + TTL_MS);
        if (includeFinancial) adminStats = e;
        else modStats = e;
    }

    public PendingFilterMeta getPendingMeta() {
        Entry<PendingFilterMeta> e = pendingMeta;
        return (e != null && e.expiresAt > System.currentTimeMillis()) ? e.value : null;
    }

    public void putPendingMeta(PendingFilterMeta meta) {
        pendingMeta = new Entry<>(meta, System.currentTimeMillis() + TTL_MS);
    }

    public void invalidate() {
        adminStats = null;
        modStats = null;
        pendingMeta = null;
    }

    public record PendingFilterMeta(long totalPending, List<String> categories, List<String> schools) {}

    private record Entry<T>(T value, long expiresAt) {}
}
