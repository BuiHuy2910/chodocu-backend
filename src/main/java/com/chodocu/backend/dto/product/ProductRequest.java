package com.chodocu.backend.dto.product;

import com.chodocu.backend.enums.ProductCondition;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dữ liệu đăng / sửa tin. Không có giá trị mặc định nào được tự điền:
 * người bán phải tự chọn tình trạng, điểm hẹn và thêm ảnh thật.
 */
@Data
public class ProductRequest {

    @NotBlank(message = "Tên sản phẩm không được để trống")
    @Size(max = 120, message = "Tên sản phẩm tối đa 120 ký tự")
    private String title;

    @Size(max = 3000, message = "Mô tả tối đa 3000 ký tự")
    private String description;

    @NotNull(message = "Giá sản phẩm không được để trống")
    @DecimalMin(value = "0", message = "Giá sản phẩm không được âm")
    private BigDecimal price;

    @DecimalMin(value = "0", message = "Giá gốc không được âm")
    private BigDecimal originalPrice;

    private Boolean isFree = false;

    private Boolean isUrgent = false;

    @NotNull(message = "Vui lòng chọn tình trạng sản phẩm")
    private ProductCondition condition;

    @Size(max = 255, message = "Ghi chú tình trạng tối đa 255 ký tự")
    private String conditionNote;

    @Size(max = 255, message = "Lý do pass đồ tối đa 255 ký tự")
    private String passReason;

    /** Phải là một trong các điểm hẹn của trường đã chọn (University.getMeetupSpots()). */
    @NotBlank(message = "Vui lòng chọn điểm hẹn giao dịch")
    private String pickupSpot;

    @NotNull(message = "Danh mục không được để trống")
    private Long categoryId;

    @NotNull(message = "Trường đại học / Khu vực không được để trống")
    private Long universityId;

    @NotEmpty(message = "Vui lòng thêm ít nhất 1 ảnh thật của sản phẩm")
    @Size(max = 5, message = "Tối đa 5 ảnh")
    private List<String> imageUrls;
}
