package com.chodocu.backend.service;

import com.chodocu.backend.dto.ReportRequest;
import com.chodocu.backend.dto.ReportResponse;
import com.chodocu.backend.entity.Product;
import com.chodocu.backend.entity.Report;
import com.chodocu.backend.entity.User;
import com.chodocu.backend.exception.ResourceNotFoundException;
import com.chodocu.backend.repository.ProductRepository;
import com.chodocu.backend.repository.ReportRepository;
import com.chodocu.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Transactional
    public ReportResponse createReport(ReportRequest request, String reporterEmail) {
        User reporter = userRepository.findByEmail(reporterEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        Product product = productRepository.findById(request.getProductId())
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

    public List<ReportResponse> getAllReports(String status) {
        List<Report> reports = (status != null && !status.equals("ALL"))
                ? reportRepository.findByStatusOrderByCreatedAtDesc(status)
                : reportRepository.findAllByOrderByCreatedAtDesc();
        return reports.stream().map(this::toDto).collect(Collectors.toList());
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
        report.setResolvedAt(LocalDateTime.now());
        return toDto(reportRepository.save(report));
    }

    public long countPendingReports() {
        return reportRepository.countByStatus("PENDING");
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
                .createdAt(r.getCreatedAt() != null ? r.getCreatedAt().format(FORMATTER) : null)
                .build();
    }
}
