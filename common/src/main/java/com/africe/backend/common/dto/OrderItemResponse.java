package com.africe.backend.common.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        String productId,
        String productTitle,
        String sku,
        String variantName,
        int quantity,
        BigDecimal unitPrice
) {}
