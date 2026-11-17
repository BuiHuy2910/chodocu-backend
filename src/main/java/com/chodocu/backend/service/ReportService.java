package com.chodocu.backend.service;

import com.chodocu.backend.dto.PageResponse;
import com.chodocu.backend.dto.ReportRequest;
import com.chodocu.backend.dto.ReportResponse;
import com.chodocu.backend.entity.Product;
import com.chodocu.backend.entity.Report;
import com.chodocu.backend.entity.User;
import com.chodocu.backend.exception.ResourceNotFoundException;
import com.chodocu.backend.repository.ProductRepository;
import com.chodocu.backend.repository.ReportRepository;
import com.chodocu.backend.repository.UserRepository;
import com.chodocu.backend.util.DisplayFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Transactional
    public ReportResponse createReport(ReportRequest request, String reporterEmail) {
        User reporter = userRepository.findByEmail(reporterEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        Product product = productRepository.findWithDetailsById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm"));
        Report report = Report.builder()
                .reporter(reporter)
                .product(product)
                .reason(request.getReason())
                .detail(request.getDetail())
                .status("PENDING")
                .build();
        return toDto(reportRepository.save(report));
    }

    public PageResponse<ReportResponse> getReportsPage(String status, String keyword, int page, int size) {
        String cleanStatus = (status == null || status.isBlank()) ? "ALL" : status.trim().toUpperCase(Locale.ROOT);
        String cleanKeyword = (keyword == null || keyword.isBlank()) ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<Object[]> rows = reportRepository.searchAdminReportRows(
                cleanStatus, cleanKeyword, PageRequest.of(Math.max(page, 0), safeSize));
        PageResponse<ReportResponse> response = PageResponse.of(rows, this::mapRow);

        long pendingCount = "PENDING".equals(cleanStatus) && cleanKeyword.isEmpty()
                ? rows.getTotalElements()
                : reportRepository.countByStatus("PENDING");
        response.setMeta(Map.of("pendingCount", pendingCount));
        return response;
    }

    @Transactional
    public ReportResponse resolveReport(Long id, String adminNote, String action, String adminEmail) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy báo cáo"));
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy admin"));
        report.setStatus(action); // RESOLVED or DISMISSED
        report.setAdminNote(adminNote);
        report.setResolvedBy(admin);
        report.setResolvedAt(DisplayFormat.nowUtc());
        return toDto(reportRepository.save(report));
    }

    public long countPendingReports() {
        return reportRepository.countByStatus("PENDING");
    }

    private ReportResponse mapRow(Object[] r) {
        return ReportResponse.builder()
                .id((Long) r[0])
                .reporterName((String) r[1])
                .productTitle(r[2] != null ? (String) r[2] : "")
                .reason((String) r[3])
                .detail((String) r[4])
                .status((String) r[5])
                .adminNote((String) r[6])
                .createdAt(DisplayFormat.dateTime((LocalDateTime) r[7]))
                .build();
    }

    private ReportResponse toDto(Report r) {
        return ReportResponse.builder()
                .id(r.getId())
                .reporterName(r.getReporter().getFullName())
                .productTitle(r.getProduct() != null ? r.getProduct().getTitle() : "")
                .reason(r.getReason())
                .detail(r.getDetail())
                .status(r.getStatus())
                .adminNote(r.getAdminNote())
                .createdAt(DisplayFormat.dateTime(r.getCreatedAt()))
                .build();
    }
}
