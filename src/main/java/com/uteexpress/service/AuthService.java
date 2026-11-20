package com.uteexpress.service;

import com.uteexpress.dto.auth.AuthTokenResponse;
import com.uteexpress.dto.auth.LoginRequest;
import com.uteexpress.dto.auth.RegisterRequest;
import com.uteexpress.entity.User;
import com.uteexpress.enums.Role;
import com.uteexpress.enums.UserStatus;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.repository.UserRepository;
import com.uteexpress.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthTokenResponse authenticate(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.getEmail().trim())
                .orElseThrow(() -> new BusinessException("Email hoặc mật khẩu không chính xác"));

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new BusinessException("Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên UTEExpress.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("Email hoặc mật khẩu không chính xác");
        }

        String token = jwtTokenProvider.generateToken(user);
        Long hubId = user.getHub() != null ? user.getHub().getId() : null;
        return new AuthTokenResponse(
                token,
                "Bearer",
                jwtTokenProvider.getExpirationMs(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                hubId
        );
    }

    @Transactional
    public User registerCustomer(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String phone = request.getPhone().trim();

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Mật khẩu xác nhận không khớp");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("Email này đã được sử dụng trong hệ thống");
        }
        if (userRepository.existsByPhone(phone)) {
            throw new BusinessException("Số điện thoại này đã được đăng ký");
        }

        User user = User.builder()
                .email(email)
                .phone(phone)
                .fullName(request.getFullName().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();

        return userRepository.save(user);
    }

    public ResponseCookie buildAuthCookie(String token) {
        return ResponseCookie.from(jwtTokenProvider.getCookieName(), token)
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .maxAge(jwtTokenProvider.getExpirationMs() / 1000)
                .build();
    }

    public ResponseCookie buildLogoutCookie() {
        return ResponseCookie.from(jwtTokenProvider.getCookieName(), "")
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .maxAge(0)
                .build();
    }

    public String resolveLandingPath(Role role) {
        return switch (role) {
            case ADMIN -> "/admin";
            case STAFF -> "/staff";
            case SHIPPER -> "/shipper/tasks";
            case CUSTOMER -> "/customer";
        };
    }
}
