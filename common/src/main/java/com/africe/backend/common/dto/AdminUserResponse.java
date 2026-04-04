package com.africe.backend.common.dto;

import java.time.Instant;

public record AdminUserResponse(
        String id,
        String email,
        String name,
        Instant createdAt
) {}
