package com.africe.backend.common.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken
) {}
