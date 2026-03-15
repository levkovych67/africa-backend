package com.africe.backend.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductRequest {

    @NotBlank
    private String title;

    private String description;

    @NotNull
    private BigDecimal basePrice;

    private List<ProductAttributeDto> attributes;

    private List<ProductVariantDto> variants;

    private String artistId;

    private List<String> images;
}
