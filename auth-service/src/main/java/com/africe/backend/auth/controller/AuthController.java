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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

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
                .orElseThrow(() -> {
                    log.warn("Login failed: email not found — {}", request.email());
                    return new IllegalArgumentException("Invalid email or password");
                });

        if (!passwordEncoder.matches(request.password(), admin.getPassword())) {
            log.warn("Login failed: wrong password for {}", request.email());
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

        log.info("Login successful for {}", request.email());
        return ResponseEntity.ok(new AuthResponse(accessToken, refreshTokenValue));
    }

    @PostMapping("/refresh")
    @RateLimiter(name = "refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> {
                    log.warn("Refresh failed: token not found");
                    return new IllegalArgumentException("Invalid refresh token");
                });

        // Delete old token FIRST to prevent race condition reuse
        refreshTokenRepository.deleteByToken(refreshToken.getToken());

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            log.warn("Refresh failed: expired token for admin {}", refreshToken.getAdminId());
            throw new IllegalArgumentException("Refresh token expired");
        }

        AdminUser admin = adminUserRepository.findById(refreshToken.getAdminId())
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

        String newAccessToken = jwtService.generateAccessToken(admin.getId(), admin.getEmail(), "ADMIN");
        String newRefreshTokenValue = jwtService.generateRefreshToken();

        RefreshToken newRefreshToken = RefreshToken.builder()
                .token(newRefreshTokenValue)
                .adminId(admin.getId())
                .expiresAt(Instant.now().plusMillis(jwtService.getRefreshTokenExpiration()))
                .build();
        refreshTokenRepository.save(newRefreshToken);

        log.info("Token refreshed for admin {}", admin.getEmail());
        return ResponseEntity.ok(new AuthResponse(newAccessToken, newRefreshTokenValue));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtService.isTokenValid(token)) {
                String adminId = jwtService.extractUserId(token);
                refreshTokenRepository.deleteByAdminId(adminId);
                log.info("Logout: all refresh tokens deleted for admin {}", adminId);
            }
        }
        return ResponseEntity.noContent().build();
    }
}
