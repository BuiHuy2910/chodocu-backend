package com.chodocu.backend.service;

import com.chodocu.backend.dto.admin.FeeCalculationResponse;
import com.chodocu.backend.dto.admin.FeeTierDto;
import com.chodocu.backend.dto.admin.FinancialSummaryResponse;
import com.chodocu.backend.dto.admin.PlatformSettingDto;
import com.chodocu.backend.dto.admin.TransactionResponse;
import com.chodocu.backend.entity.PlatformFeeTier;
import com.chodocu.backend.entity.PlatformSetting;
import com.chodocu.backend.entity.User;
import com.chodocu.backend.exception.BadRequestException;
import com.chodocu.backend.exception.ResourceNotFoundException;
import com.chodocu.backend.repository.PlatformFeeTierRepository;
import com.chodocu.backend.repository.PlatformSettingRepository;
import com.chodocu.backend.repository.TransactionRepository;
import com.chodocu.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlatformSettingService {

    private final PlatformSettingRepository settingRepository;
    private final PlatformFeeTierRepository feeTierRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static final String FEE_RATE = "platform_fee_rate";
    public static final String MIN_FEE = "min_platform_fee";
    public static final String MAX_FEE = "max_platform_fee";
    public static final String FEE_ENABLED = "fee_enabled";
    public static final String FEE_PAYER = "fee_payer";
    public static final String FEE_ROUND_STEP = "fee_round_step";

    public BigDecimal getCurrentFeeRate() {
        return settingRepository.findByKey(FEE_RATE)
                .map(s -> new BigDecimal(s.getValue()))
                .orElse(new BigDecimal("0.05"));
    }

    public BigDecimal getMinFee() {
        return settingRepository.findByKey(MIN_FEE)
                .map(s -> new BigDecimal(s.getValue()))
                .orElse(new BigDecimal("2000"));
    }

    /** Trả về 0 nếu admin đặt "không giới hạn". */
    public BigDecimal getMaxFee() {
        return settingRepository.findByKey(MAX_FEE)
                .map(s -> new BigDecimal(s.getValue()))
                .orElse(new BigDecimal("50000"));
    }

    public boolean isFeeEnabled() {
        return settingRepository.findByKey(FEE_ENABLED)
                .map(s -> Boolean.parseBoolean(s.getValue()))
                .orElse(true);
    }

    public String getFeePayer() {
        return settingRepository.findByKey(FEE_PAYER)
                .map(s -> s.getValue().toUpperCase(Locale.ROOT))
                .orElse("SELLER");
    }

    public BigDecimal getRoundStep() {
        return settingRepository.findByKey(FEE_ROUND_STEP)
                .map(s -> new BigDecimal(s.getValue()))
                .orElse(new BigDecimal("1000"));
    }

    public boolean isPostApprovalRequired() {
        return settingRepository.findByKey("post_require_approval")
                .map(s -> Boolean.parseBoolean(s.getValue()))
                .orElse(true);
    }

    /** Bậc phí áp dụng cho giá trị đơn, null nếu dùng tỷ lệ cơ bản. */
    public PlatformFeeTier resolveTier(BigDecimal amount) {
        if (amount == null) return null;
        return feeTierRepository.findByActiveTrueOrderByPriorityDesc().stream()
                .filter(t -> t.matches(amount))
                .findFirst()
                .orElse(null);
    }

    /** Tỷ lệ phí hiện hành cho một đơn cụ thể (ưu tiên bậc phí). */
    public BigDecimal resolveFeeRate(BigDecimal amount) {
        PlatformFeeTier tier = resolveTier(amount);
        return tier != null ? tier.getFeeRate() : getCurrentFeeRate();
    }

    /** Tính nhanh số tiền phí sàn. */
    public BigDecimal calculatePlatformFee(BigDecimal amount) {
        return calculate(amount).getPlatformFeeAmount();
    }

    /**
     * Tính đầy đủ: tỷ lệ, phí sàn, tiền người bán nhận, tiền người mua trả.
     * Quy tắc: ưu tiên bậc phí -> % cơ bản -> min/max -> làm tròn theo bước.
     */
    public FeeCalculationResponse calculate(BigDecimal amount) {
        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;
        boolean enabled = isFeeEnabled();
        String payer = getFeePayer();
        PlatformFeeTier tier = resolveTier(safeAmount);
        BigDecimal rate = tier != null ? tier.getFeeRate() : getCurrentFeeRate();

        BigDecimal fee = BigDecimal.ZERO;
        if (enabled) {
            fee = safeAmount.multiply(rate);

            BigDecimal step = getRoundStep();
            if (step != null && step.compareTo(BigDecimal.ZERO) > 0) {
                fee = fee.divide(step, 0, RoundingMode.HALF_UP).multiply(step);
            } else {
                fee = fee.setScale(0, RoundingMode.HALF_UP);
            }

            BigDecimal minFee = getMinFee();
            BigDecimal maxFee = getMaxFee();
            if (fee.compareTo(minFee) < 0) fee = minFee;
            if (maxFee != null && maxFee.compareTo(BigDecimal.ZERO) > 0 && fee.compareTo(maxFee) > 0) fee = maxFee;
            if (fee.compareTo(safeAmount) > 0) fee = safeAmount; // không thu quá giá trị đơn
        }

        BigDecimal sellerReceive = safeAmount;
        BigDecimal buyerPay = safeAmount;

        switch (payer) {
            case "BUYER" -> buyerPay = safeAmount.add(fee);
            case "SPLIT" -> {
                BigDecimal half = fee.divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP);
                sellerReceive = safeAmount.subtract(half);
                buyerPay = safeAmount.add(fee.subtract(half));
            }
            default -> sellerReceive = safeAmount.subtract(fee); // SELLER
        }

        return FeeCalculationResponse.builder()
                .orderAmount(safeAmount)
                .feeRate(rate)
                .feeRatePercent(rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString())
                .platformFeeAmount(fee)
                .sellerReceiveAmount(sellerReceive)
                .buyerPayAmount(buyerPay)
                .feePayer(payer)
                .appliedTierName(tier != null ? tier.getName() : "Tỷ lệ cơ bản")
                .feeEnabled(enabled)
                .build();
    }

    public List<PlatformSettingDto> getAllSettings() {
        return settingRepository.findAll().stream()
                .map(s -> PlatformSettingDto.builder()
                        .key(s.getKey())
                        .value(s.getValue())
                        .description(s.getDescription())
                        .updatedAt(s.getUpdatedAt() != null ? s.getUpdatedAt().format(FORMATTER) : null)
                        .updatedBy(s.getUpdatedBy() != null ? s.getUpdatedBy().getFullName() : null)
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public PlatformSettingDto updateSetting(String key, String value, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy admin"));
        validateSetting(key, value);

        PlatformSetting setting = settingRepository.findByKey(key)
                .orElse(PlatformSetting.builder().key(key).build());
        setting.setValue(value);
        setting.setUpdatedBy(admin);
        return toDto(settingRepository.save(setting));
    }

    private void validateSetting(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Giá trị cài đặt không được để trống");
        }
        try {
            switch (key) {
                case FEE_RATE -> {
                    BigDecimal rate = new BigDecimal(value);
                    if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
                        throw new BadRequestException("Tỷ lệ phí phải nằm trong khoảng 0 - 1 (0.05 = 5%)");
                    }
                }
                case MIN_FEE, MAX_FEE, FEE_ROUND_STEP -> {
                    if (new BigDecimal(value).compareTo(BigDecimal.ZERO) < 0) {
                        throw new BadRequestException("Giá trị tiền không được âm");
                    }
                }
                case FEE_ENABLED, "post_require_approval" -> {
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                        throw new BadRequestException("Giá trị phải là true hoặc false");
                    }
                }
                case FEE_PAYER -> {
                    String v = value.toUpperCase(Locale.ROOT);
                    if (!v.equals("SELLER") && !v.equals("BUYER") && !v.equals("SPLIT")) {
                        throw new BadRequestException("Bên chịu phí chỉ có thể là SELLER, BUYER hoặc SPLIT");
                    }
                }
                default -> { /* setting văn bản tự do */ }
            }
        } catch (NumberFormatException e) {
            throw new BadRequestException("Giá trị không phải số hợp lệ: " + value);
        }
    }

    private PlatformSettingDto toDto(PlatformSetting s) {
        return PlatformSettingDto.builder()
                .key(s.getKey())
                .value(s.getValue())
                .description(s.getDescription())
                .updatedAt(s.getUpdatedAt() != null ? s.getUpdatedAt().format(FORMATTER) : null)
                .updatedBy(s.getUpdatedBy() != null ? s.getUpdatedBy().getFullName() : null)
                .build();
    }

    // ================= FEE TIERS (admin CRUD) =================

    public List<FeeTierDto> getAllFeeTiers() {
        return feeTierRepository.findAllByOrderByPriorityDesc().stream()
                .map(this::toTierDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public FeeTierDto saveFeeTier(Long id, FeeTierDto dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BadRequestException("Tên bậc phí không được để trống");
        }
        if (dto.getFeeRate() == null || dto.getFeeRate().compareTo(BigDecimal.ZERO) < 0
                || dto.getFeeRate().compareTo(BigDecimal.ONE) > 0) {
            throw new BadRequestException("Tỷ lệ phí phải nằm trong khoảng 0 - 1");
        }
        PlatformFeeTier tier = id != null
                ? feeTierRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bậc phí"))
                : new PlatformFeeTier();

        tier.setName(dto.getName().trim());
        tier.setMinAmount(dto.getMinAmount() != null ? dto.getMinAmount() : BigDecimal.ZERO);
        tier.setMaxAmount(dto.getMaxAmount());
        tier.setFeeRate(dto.getFeeRate());
        tier.setActive(dto.getActive() == null || dto.getActive());
        tier.setPriority(dto.getPriority() != null ? dto.getPriority() : 0);

        if (tier.getMaxAmount() != null && tier.getMaxAmount().compareTo(tier.getMinAmount()) < 0) {
            throw new BadRequestException("Giá trị 'đến' phải lớn hơn hoặc bằng giá trị 'từ'");
        }
        return toTierDto(feeTierRepository.save(tier));
    }

    @Transactional
    public void deleteFeeTier(Long id) {
        PlatformFeeTier tier = feeTierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bậc phí"));
        // Chỉ tắt thay vì xóa để giữ lịch sử đối chiếu
        tier.setActive(false);
        feeTierRepository.save(tier);
    }

    private FeeTierDto toTierDto(PlatformFeeTier t) {
        return FeeTierDto.builder()
                .id(t.getId())
                .name(t.getName())
                .minAmount(t.getMinAmount())
                .maxAmount(t.getMaxAmount())
                .feeRate(t.getFeeRate())
                .active(t.getActive())
                .priority(t.getPriority())
                .build();
    }

    public FinancialSummaryResponse getFinancialSummary() {
        List<TransactionResponse> recent = transactionRepository.findAllByOrderByCreatedAtDesc()
                .stream().limit(50)
                .map(t -> TransactionResponse.builder()
                        .id(t.getId())
                        .orderId(t.getOrder().getId())
                        .productTitle(t.getProduct().getTitle())
                        .buyerName(t.getBuyer().getFullName())
                        .sellerName(t.getSeller().getFullName())
                        .transactionAmount(t.getTransactionAmount())
                        .platformFeeRate(t.getPlatformFeeRate())
                        .platformFeeAmount(t.getPlatformFeeAmount())
                        .sellerReceiveAmount(t.getSellerReceiveAmount())
                        .createdAt(t.getCreatedAt() != null ? t.getCreatedAt().format(FORMATTER) : null)
                        .build())
                .collect(Collectors.toList());

        return FinancialSummaryResponse.builder()
                .totalTransactionValue(transactionRepository.sumTotalTransactionValue())
                .totalPlatformFees(transactionRepository.sumTotalPlatformFees())
                .totalCompletedOrders((long) recent.size())
                .currentFeeRate(getCurrentFeeRate())
                .minFee(getMinFee())
                .maxFee(getMaxFee())
                .roundStep(getRoundStep())
                .feePayer(getFeePayer())
                .feeEnabled(isFeeEnabled())
                .recentTransactions(recent)
                .build();
    }
}
