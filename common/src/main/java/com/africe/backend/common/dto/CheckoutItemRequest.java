package com.africe.backend.common.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CheckoutItemRequest(
        @NotBlank String productId,
        @NotBlank String sku,
        @Min(1) int quantity
) {}
