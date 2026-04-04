package com.africe.backend.common.dto;

import java.math.BigDecimal;

public record TopProductDto(
        String productId,
        String productTitle,
        long totalQuantity,
        BigDecimal totalRevenue
) {}
