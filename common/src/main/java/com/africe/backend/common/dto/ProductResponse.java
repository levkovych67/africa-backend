package com.africe.backend.common.dto;

import com.africe.backend.common.model.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProductResponse(
        String id,
        String slug,
        String title,
        String description,
        BigDecimal basePrice,
        List<ProductAttributeDto> attributes,
        List<ProductVariantDto> variants,
        List<String> images,
        String artistId,
        ProductStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
