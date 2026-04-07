package com.africe.backend.common.dto;

import java.math.BigDecimal;
import java.util.Map;

public record ProductVariantDto(
        String sku,
        Map<String, String> attributes,
        BigDecimal price,
        int stock
) {}
