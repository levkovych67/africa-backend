package com.africe.backend.common.dto;

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
public class UpdateProductRequest {

    private String title;

    private String description;

    private BigDecimal basePrice;

    private List<ProductAttributeDto> attributes;

    private List<ProductVariantDto> variants;

    private String artistId;

    private List<String> images;
}
