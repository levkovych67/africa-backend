package com.africe.backend.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariant {

    private String sku;
    private Map<String, String> attributes;
    @NotNull(message = "Variant price is required")
    @DecimalMin(value = "0.01", message = "Variant price must be greater than 0")
    private BigDecimal price;
    private int stock;
}
