package com.africe.backend.common.dto;

import com.africe.backend.common.model.ProductStatus;

import java.math.BigDecimal;
import java.util.List;

public record ProductUpdateEvent(
        String eventType,
        String productId,
        String slug,
        BigDecimal minPrice,
        List<ProductVariantDto> variants,
        List<ProductAttributeDto> attributes,
        ProductStatus status
) {
    public static final String STOCK_CHANGED = "STOCK_CHANGED";
    public static final String PRODUCT_UPDATED = "PRODUCT_UPDATED";
    public static final String PRODUCT_DELETED = "PRODUCT_DELETED";
}
