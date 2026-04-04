package com.africe.backend.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-chars-long!!";
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 3600000, 604800000);
    }

    @Test
    void generateAccessToken_returnsValidToken() {
        String token = jwtService.generateAccessToken("user123", "admin@test.com");

        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void extractUserId_returnsCorrectSubject() {
        String token = jwtService.generateAccessToken("user123", "admin@test.com");

        assertThat(jwtService.extractUserId(token)).isEqualTo("user123");
    }

    @Test
    void extractEmail_returnsCorrectClaim() {
        String token = jwtService.generateAccessToken("user123", "admin@test.com");

        assertThat(jwtService.extractEmail(token)).isEqualTo("admin@test.com");
    }

    @Test
    void isTokenValid_returnsFalseForGarbage() {
        assertThat(jwtService.isTokenValid("not-a-real-token")).isFalse();
    }

    @Test
    void isTokenValid_returnsFalseForTamperedToken() {
        String token = jwtService.generateAccessToken("user123", "admin@test.com");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @Test
    void isTokenValid_returnsFalseForWrongSecret() {
        JwtService otherService = new JwtService(
                "another-secret-key-that-is-at-least-32-chars!!", 3600000, 604800000);
        String token = otherService.generateAccessToken("user123", "admin@test.com");

        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void generateRefreshToken_returnsUUID() {
        String token = jwtService.generateRefreshToken();

        assertThat(token).isNotBlank();
        assertThat(token).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void expiredToken_isInvalid() {
        // Token with 0ms expiration = immediately expired
        JwtService expiredService = new JwtService(SECRET, 0, 0);
        String token = expiredService.generateAccessToken("user123", "admin@test.com");

        assertThat(jwtService.isTokenValid(token)).isFalse();
    }
}
