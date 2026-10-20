package com.chodocu.backend.service;

import com.chodocu.backend.dto.auth.AuthResponse;
import com.chodocu.backend.dto.auth.LoginRequest;
import com.chodocu.backend.dto.auth.RegisterRequest;
import com.chodocu.backend.dto.auth.UserSummaryDto;
import com.chodocu.backend.entity.University;
import com.chodocu.backend.entity.User;
import com.chodocu.backend.enums.Role;
import com.chodocu.backend.exception.BadRequestException;
import com.chodocu.backend.exception.ResourceNotFoundException;
import com.chodocu.backend.repository.UniversityRepository;
import com.chodocu.backend.repository.UserRepository;
import com.chodocu.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UniversityRepository universityRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final DtoMapper dtoMapper;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email sinh viên này đã được đăng ký!");
        }

        University university = null;
        if (request.getUniversityId() != null) {
            university = universityRepository.findById(request.getUniversityId()).orElse(null);
        }
        if (university == null) {
            university = universityRepository.findAll().stream().findFirst().orElse(null);
        }

        boolean isEduEmail = email.endsWith(".edu.vn") || email.contains(".edu.");

        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .studentId(request.getStudentId() != null ? request.getStudentId() : "SV" + (System.currentTimeMillis() % 100000))
                .phone(request.getPhone())
                .university(university)
                .dormArea(request.getDormArea() != null && !request.getDormArea().isBlank()
                        ? request.getDormArea() : "KTX Khu B - ĐHQG")
                .avatarUrl("https://api.dicebear.com/7.x/notionists/svg?seed=" + email)
                .role(Role.STUDENT)
                .verifiedStudent(isEduEmail || request.getStudentId() != null)
                .active(true)
                .rating(5.0)
                .completedDeals(0)
                .build();

        User saved = userRepository.save(user);
        String token = jwtTokenProvider.generateTokenFromEmail(saved.getEmail(), saved.getId(), saved.getRole().name());

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .user(dtoMapper.toUserSummary(saved))
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail().trim().toLowerCase(), request.getPassword())
        );

        String token = jwtTokenProvider.generateToken(authentication);
        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new BadRequestException("Tài khoản của bạn đã bị khóa bởi Quản trị viên");
        }

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .user(dtoMapper.toUserSummary(user))
                .build();
    }

    public UserSummaryDto getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
        return dtoMapper.toUserSummary(user);
    }

    @Transactional
    public UserSummaryDto updateProfile(String email, String fullName, String phone, String dormArea, Long universityId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));

        if (fullName != null && !fullName.isBlank()) user.setFullName(fullName.trim());
        if (phone != null) user.setPhone(phone.trim());
        if (dormArea != null) user.setDormArea(dormArea.trim());
        if (universityId != null) {
            universityRepository.findById(universityId).ifPresent(user::setUniversity);
        }

        return dtoMapper.toUserSummary(userRepository.save(user));
    }
}
