package com.africe.backend.auth.controller;

import com.africe.backend.auth.repository.AdminUserRepository;
import com.africe.backend.auth.repository.RefreshTokenRepository;
import com.africe.backend.auth.service.JwtService;
import com.africe.backend.common.dto.AuthResponse;
import com.africe.backend.common.dto.LoginRequest;
import com.africe.backend.common.dto.RefreshTokenRequest;
import com.africe.backend.common.model.AdminUser;
import com.africe.backend.common.model.RefreshToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock AdminUserRepository adminUserRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock JwtService jwtService;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks AuthController authController;

    @Test
    void login_success() {
        AdminUser admin = AdminUser.builder()
                .id("admin1").email("admin@test.com").password("hashed").build();

        when(adminUserRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtService.generateAccessToken("admin1", "admin@test.com", "ADMIN")).thenReturn("access-jwt");
        when(jwtService.generateRefreshToken()).thenReturn("refresh-uuid");
        when(jwtService.getRefreshTokenExpiration()).thenReturn(604800000L);

        ResponseEntity<AuthResponse> response = authController.login(
                new LoginRequest("admin@test.com", "password123"));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isEqualTo("access-jwt");
        assertThat(response.getBody().refreshToken()).isEqualTo("refresh-uuid");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void login_wrongEmail_throws() {
        when(adminUserRepository.findByEmail("wrong@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authController.login(
                new LoginRequest("wrong@test.com", "password123")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void login_wrongPassword_throws() {
        AdminUser admin = AdminUser.builder()
                .id("admin1").email("admin@test.com").password("hashed").build();
        when(adminUserRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authController.login(
                new LoginRequest("admin@test.com", "wrong")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refresh_success() {
        RefreshToken rt = RefreshToken.builder()
                .token("valid-refresh").adminId("admin1")
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        AdminUser admin = AdminUser.builder()
                .id("admin1").email("admin@test.com").build();

        when(refreshTokenRepository.findByToken("valid-refresh")).thenReturn(Optional.of(rt));
        when(adminUserRepository.findById("admin1")).thenReturn(Optional.of(admin));
        when(jwtService.generateAccessToken("admin1", "admin@test.com", "ADMIN")).thenReturn("new-access");
        when(jwtService.generateRefreshToken()).thenReturn("new-refresh");
        when(jwtService.getRefreshTokenExpiration()).thenReturn(604800000L);

        ResponseEntity<AuthResponse> response = authController.refresh(
                new RefreshTokenRequest("valid-refresh"));

        assertThat(response.getBody().accessToken()).isEqualTo("new-access");
        assertThat(response.getBody().refreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenRepository).deleteByToken("valid-refresh");
    }

    @Test
    void refresh_expiredToken_throws() {
        RefreshToken rt = RefreshToken.builder()
                .token("expired").adminId("admin1")
                .expiresAt(Instant.now().minusSeconds(3600)).build();

        when(refreshTokenRepository.findByToken("expired")).thenReturn(Optional.of(rt));

        assertThatThrownBy(() -> authController.refresh(new RefreshTokenRequest("expired")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }
}
