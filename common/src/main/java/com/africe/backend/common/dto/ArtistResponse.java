package com.africe.backend.common.dto;

import java.time.Instant;
import java.util.Map;

public record ArtistResponse(
        String id,
        String slug,
        String name,
        String bio,
        String image,
        Map<String, String> socialLinks,
        Instant createdAt,
        Instant updatedAt
) {}
