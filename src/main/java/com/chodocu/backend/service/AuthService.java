package com.chodocu.backend.service;

import com.chodocu.backend.dto.auth.AuthResponse;
import com.chodocu.backend.dto.auth.ChangePasswordRequest;
import com.chodocu.backend.dto.auth.LoginRequest;
import com.chodocu.backend.dto.auth.RegisterRequest;
import com.chodocu.backend.dto.auth.UpdateProfileRequest;
import com.chodocu.backend.dto.auth.UserSummaryDto;
import com.chodocu.backend.entity.University;
import com.chodocu.backend.entity.User;
import com.chodocu.backend.enums.Role;
import com.chodocu.backend.exception.BadRequestException;
import com.chodocu.backend.exception.ResourceNotFoundException;
import com.chodocu.backend.repository.UniversityRepository;
import com.chodocu.backend.repository.UserRepository;
import com.chodocu.backend.security.CustomUserDetailsService;
import com.chodocu.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Đăng ký, đăng nhập và quản lý hồ sơ cá nhân.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final UniversityRepository universityRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final DtoMapper dtoMapper;

    /** Tạo tài khoản mới rồi trả token để người dùng vào app ngay. */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email này đã được đăng ký");
        }

        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone().trim())
                .university(findUniversity(request.getUniversityId()))
                .role(Role.STUDENT)
                .active(true)
                .build();

        return buildAuthResponse(userRepository.save(user));
    }

    /**
     * Đăng nhập bằng email + mật khẩu.
     * Sai thông tin -> BadCredentialsException (401), tài khoản bị khóa -> LockedException (403).
     */
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        return buildAuthResponse(findUserByEmail(email));
    }

    public UserSummaryDto getCurrentUser(String email) {
        return dtoMapper.toUserSummary(findUserByEmail(email));
    }

    /** Cập nhật hồ sơ: chỉ thay đổi những trường được gửi lên. */
    @Transactional
    public UserSummaryDto updateProfile(UpdateProfileRequest request, String email) {
        User user = findUserByEmail(email);

        if (StringUtils.hasText(request.getFullName())) {
            user.setFullName(request.getFullName().trim());
        }
        if (StringUtils.hasText(request.getPhone())) {
            user.setPhone(validatePhone(request.getPhone()));
        }
        if (request.getDormArea() != null) {
            user.setDormArea(blankToNull(request.getDormArea()));
        }
        if (request.getBio() != null) {
            user.setBio(blankToNull(request.getBio()));
        }
        if (StringUtils.hasText(request.getAvatarUrl())) {
            String url = request.getAvatarUrl().trim();
            if (!isWebUrl(url)) {
                throw new BadRequestException("Đường dẫn ảnh không hợp lệ, vui lòng tải ảnh lên lại.");
            }
            user.setAvatarUrl(url);
        }
        if (request.getUniversityId() != null) {
            user.setUniversity(findUniversity(request.getUniversityId()));
        }
        User saved = userRepository.save(user);
        userDetailsService.evict(saved.getEmail());
        return dtoMapper.toUserSummary(saved);
    }

    /** Đổi ảnh đại diện; avatarUrl rỗng nghĩa là gỡ ảnh (giống hàm api_update_avatar trong SQL). */
    @Transactional
    public UserSummaryDto updateAvatar(String email, String avatarUrl) {
        User user = findUserByEmail(email);
        String url = blankToNull(avatarUrl);
        if (url != null && !isWebUrl(url)) {
            throw new BadRequestException("Đường dẫn ảnh không hợp lệ, vui lòng tải ảnh lên lại.");
        }
        user.setAvatarUrl(url);
        User saved = userRepository.save(user);
        userDetailsService.evict(saved.getEmail());
        return dtoMapper.toUserSummary(saved);
    }

    /** Đổi mật khẩu: bắt buộc nhập đúng mật khẩu hiện tại. */
    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = findUserByEmail(email);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Mật khẩu hiện tại không đúng");
        }
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new BadRequestException("Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        userDetailsService.evict(user.getEmail());
    }

    // ----------------------------------------------------------------
    // Hàm hỗ trợ
    // ----------------------------------------------------------------

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtTokenProvider.generateTokenFromEmail(user.getEmail(), user.getId(), user.getRole().name());
        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .user(dtoMapper.toUserSummary(user))
                .build();
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
    }

    private University findUniversity(Long universityId) {
        return universityRepository.findById(universityId)
                .orElseThrow(() -> new ResourceNotFoundException("Trường / khu vực không tồn tại"));
    }

    private String validatePhone(String phone) {
        String trimmed = phone.trim();
        if (!trimmed.matches(RegisterRequest.PHONE_REGEX)) {
            throw new BadRequestException("Số điện thoại không hợp lệ");
        }
        return trimmed;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** Link ảnh phải là http(s) do máy chủ upload trả về, tối đa 1000 ký tự. */
    private boolean isWebUrl(String url) {
        String lower = url.toLowerCase();
        return (lower.startsWith("http://") || lower.startsWith("https://")) && url.length() <= 1000;
    }
}
