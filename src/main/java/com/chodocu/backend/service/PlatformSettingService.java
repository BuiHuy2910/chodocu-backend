package com.chodocu.backend.service;

import com.chodocu.backend.dto.PageResponse;
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
import com.chodocu.backend.util.DisplayFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlatformSettingService {

    private static final long CACHE_TTL_MS = 5 * 60_000L; // 5 phút, tự làm mới ngay khi admin cập nhật
    private static final int MAX_PAGE_SIZE = 50;

    private final PlatformSettingRepository settingRepository;
    private final PlatformFeeTierRepository feeTierRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public static final String FEE_RATE = "platform_fee_rate";
    public static final String MIN_FEE = "min_platform_fee";
    public static final String MAX_FEE = "max_platform_fee";
    public static final String FEE_ENABLED = "fee_enabled";
    public static final String FEE_PAYER = "fee_payer";
    public static final String FEE_ROUND_STEP = "fee_round_step";

    private volatile Map<String, String> cachedSettings;
    private volatile long settingsExpireAt;

    private volatile List<PlatformFeeTier> cachedActiveTiers;
    private volatile long tiersExpireAt;

    public void invalidateCache() {
        cachedSettings = null;
        settingsExpireAt = 0;
        cachedActiveTiers = null;
        tiersExpireAt = 0;
    }

    private Map<String, String> settingsMap() {
        long now = System.currentTimeMillis();
        Map<String, String> map = cachedSettings;
        if (map == null || now >= settingsExpireAt) {
            Map<String, String> fresh = settingRepository.findAll().stream()
                    .collect(Collectors.toUnmodifiableMap(PlatformSetting::getKey, PlatformSetting::getValue, (a, b) -> b));
            cachedSettings = fresh;
            settingsExpireAt = now + CACHE_TTL_MS;
            return fresh;
        }
        return map;
    }

    private List<PlatformFeeTier> activeTiers() {
        long now = System.currentTimeMillis();
        List<PlatformFeeTier> list = cachedActiveTiers;
        if (list == null || now >= tiersExpireAt) {
            List<PlatformFeeTier> fresh = List.copyOf(feeTierRepository.findByActiveTrueOrderByPriorityDesc());
            cachedActiveTiers = fresh;
            tiersExpireAt = now + CACHE_TTL_MS;
            return fresh;
        }
        return list;
    }

    private String getSettingValue(String key) {
        return settingsMap().get(key);
    }

    public BigDecimal getCurrentFeeRate() {
        String v = getSettingValue(FEE_RATE);
        return v != null ? new BigDecimal(v) : new BigDecimal("0.05");
    }

    public BigDecimal getMinFee() {
        String v = getSettingValue(MIN_FEE);
        return v != null ? new BigDecimal(v) : new BigDecimal("2000");
    }

    /** Trả về 0 nếu admin đặt "không giới hạn". */
    public BigDecimal getMaxFee() {
        String v = getSettingValue(MAX_FEE);
        return v != null ? new BigDecimal(v) : new BigDecimal("50000");
    }

    public boolean isFeeEnabled() {
        String v = getSettingValue(FEE_ENABLED);
        return v == null || Boolean.parseBoolean(v);
    }

    public String getFeePayer() {
        String v = getSettingValue(FEE_PAYER);
        return v != null ? v.toUpperCase(Locale.ROOT) : "SELLER";
    }

    public BigDecimal getRoundStep() {
        String v = getSettingValue(FEE_ROUND_STEP);
        return v != null ? new BigDecimal(v) : new BigDecimal("1000");
    }

    public boolean isPostApprovalRequired() {
        String v = getSettingValue("post_require_approval");
        return v == null || Boolean.parseBoolean(v);
    }

    /** Bậc phí áp dụng cho giá trị đơn, null nếu dùng tỷ lệ cơ bản. */
    public PlatformFeeTier resolveTier(BigDecimal amount) {
        if (amount == null) return null;
        return activeTiers().stream()
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
        return settingRepository.findAllWithUpdatedBy().stream()
                .map(this::toDto)
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
        PlatformSetting saved = settingRepository.save(setting);
        invalidateCache();
        return toDto(saved);
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
                .updatedAt(DisplayFormat.dateTime(s.getUpdatedAt()))
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
        PlatformFeeTier saved = feeTierRepository.save(tier);
        invalidateCache();
        return toTierDto(saved);
    }

    @Transactional
    public void deleteFeeTier(Long id) {
        PlatformFeeTier tier = feeTierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bậc phí"));
        // Chỉ tắt thay vì xóa để giữ lịch sử đối chiếu
        tier.setActive(false);
        feeTierRepository.save(tier);
        invalidateCache();
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

    // ================= TÀI CHÍNH & GIAO DỊCH =================

    public PageResponse<TransactionResponse> getTransactionsPage(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<Object[]> rows = transactionRepository.findTransactionRows(PageRequest.of(Math.max(page, 0), safeSize));
        return PageResponse.of(rows, this::mapTransactionRow);
    }

    public FinancialSummaryResponse getFinancialSummary(int page, int size) {
        PageResponse<TransactionResponse> txPage = getTransactionsPage(page, size);

        List<Object[]> totalsRows = transactionRepository.getFinancialTotals();
        long completedCount = 0L;
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalFees = BigDecimal.ZERO;
        if (!totalsRows.isEmpty()) {
            Object[] r = totalsRows.get(0);
            completedCount = r[0] != null ? ((Number) r[0]).longValue() : 0L;
            totalValue = r[1] != null ? (BigDecimal) r[1] : BigDecimal.ZERO;
            totalFees = r[2] != null ? (BigDecimal) r[2] : BigDecimal.ZERO;
        }

        Map<String, FeeCalculationResponse> previews = new LinkedHashMap<>();
        previews.put("50k", calculate(new BigDecimal("50000")));
        previews.put("500k", calculate(new BigDecimal("500000")));
        previews.put("2m", calculate(new BigDecimal("2000000")));

        return FinancialSummaryResponse.builder()
                .totalTransactionValue(totalValue)
                .totalPlatformFees(totalFees)
                .totalCompletedOrders(completedCount)
                .currentFeeRate(getCurrentFeeRate())
                .minFee(getMinFee())
                .maxFee(getMaxFee())
                .roundStep(getRoundStep())
                .feePayer(getFeePayer())
                .feeEnabled(isFeeEnabled())
                .recentTransactions(txPage.getContent())
                .transactionsPage(txPage)
                .feeTiers(getAllFeeTiers())
                .feePreviews(previews)
                .build();
    }

    private TransactionResponse mapTransactionRow(Object[] r) {
        return TransactionResponse.builder()
                .id((Long) r[0])
                .orderId((Long) r[1])
                .productTitle((String) r[2])
                .buyerName((String) r[3])
                .sellerName((String) r[4])
                .transactionAmount((BigDecimal) r[5])
                .platformFeeRate((BigDecimal) r[6])
                .platformFeeAmount((BigDecimal) r[7])
                .sellerReceiveAmount((BigDecimal) r[8])
                .createdAt(DisplayFormat.dateTime((LocalDateTime) r[9]))
                .build();
    }
}
