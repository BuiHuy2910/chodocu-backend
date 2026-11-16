package com.chodocu.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "universities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class University {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String shortName;

    private String emailDomain;

    private String address;

    /** Các điểm hẹn an toàn, lưu dạng "Sảnh A, Thư viện B, Căng tin C". */
    @Column(length = 500)
    private String safeMeetupSpots;

    /**
     * Tách safeMeetupSpots thành danh sách để app cho người dùng chọn.
     * JPA dùng field access (vì @Id đặt trên field) nên getter này không tạo cột mới,
     * còn Jackson vẫn trả về trong JSON với tên "meetupSpots".
     */
    public List<String> getMeetupSpots() {
        if (safeMeetupSpots == null || safeMeetupSpots.isBlank()) {
            return List.of();
        }
        return Arrays.stream(safeMeetupSpots.split(","))
                .map(String::trim)
                .filter(spot -> !spot.isEmpty())
                .toList();
    }

    /** Điểm hẹn có nằm trong danh sách chuẩn của trường không. */
    public boolean hasMeetupSpot(String spot) {
        return spot != null && getMeetupSpots().contains(spot.trim());
    }
}
