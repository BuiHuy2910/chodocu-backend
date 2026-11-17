package com.chodocu.backend.dto.admin;

import com.chodocu.backend.dto.PageResponse;
import com.chodocu.backend.dto.product.ProductResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Gói toàn bộ dữ liệu màn "Duyệt bài đăng" theo trang trong 1 request duy nhất:
 * danh sách tin chờ duyệt (phân trang), danh mục/trường đang có trong hàng chờ,
 * thống kê tin của từng người bán, tin cùng người bán (kiểm tra trùng) và lịch sử duyệt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingQueueResponse {
    private PageResponse<ProductResponse> page;
    private long totalPending;
    private List<String> categories;
    private List<String> schools;
    private Map<Long, Map<String, Long>> sellerStats;
    private Map<Long, List<Map<String, Object>>> sellerSiblings;
    private Map<Long, List<Map<String, Object>>> logsByProduct;
}
