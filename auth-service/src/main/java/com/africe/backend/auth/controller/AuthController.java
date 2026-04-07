package com.africe.backend.auth.controller;

import com.africe.backend.auth.repository.AdminUserRepository;
import com.africe.backend.auth.repository.RefreshTokenRepository;
import com.africe.backend.auth.service.JwtService;
import com.africe.backend.common.dto.AuthResponse;
import com.africe.backend.common.dto.LoginRequest;
import com.africe.backend.common.dto.RefreshTokenRequest;
import com.africe.backend.common.model.AdminUser;
import com.africe.backend.common.model.RefreshToken;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AdminUserRepository adminUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AdminUserRepository adminUserRepository,
                          RefreshTokenRepository refreshTokenRepository,
                          JwtService jwtService,
                          PasswordEncoder passwordEncoder) {
        this.adminUserRepository = adminUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    @RateLimiter(name = "login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AdminUser admin = adminUserRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), admin.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        String accessToken = jwtService.generateAccessToken(admin.getId(), admin.getEmail(), "ADMIN");
        String refreshTokenValue = jwtService.generateRefreshToken();

        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenValue)
                .adminId(admin.getId())
                .expiresAt(Instant.now().plusMillis(jwtService.getRefreshTokenExpiration()))
                .build();
        refreshTokenRepository.save(refreshToken);

        return ResponseEntity.ok(new AuthResponse(accessToken, refreshTokenValue));
    }

    @PostMapping("/refresh")
    @RateLimiter(name = "login")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepository.deleteByToken(refreshToken.getToken());
            throw new IllegalArgumentException("Refresh token expired");
        }

        AdminUser admin = adminUserRepository.findById(refreshToken.getAdminId())
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

        // Delete old refresh token and create new one
        refreshTokenRepository.deleteByToken(refreshToken.getToken());

        String newAccessToken = jwtService.generateAccessToken(admin.getId(), admin.getEmail(), "ADMIN");
        String newRefreshTokenValue = jwtService.generateRefreshToken();

        RefreshToken newRefreshToken = RefreshToken.builder()
                .token(newRefreshTokenValue)
                .adminId(admin.getId())
                .expiresAt(Instant.now().plusMillis(jwtService.getRefreshTokenExpiration()))
                .build();
        refreshTokenRepository.save(newRefreshToken);

        return ResponseEntity.ok(new AuthResponse(newAccessToken, newRefreshTokenValue));
    }
}
