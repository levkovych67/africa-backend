package com.africe.backend.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private String id;
    private String slug;
    private String title;
    private String description;
    private BigDecimal basePrice;
    private List<ProductAttributeDto> attributes;
    private List<ProductVariantDto> variants;
    private List<String> images;
    private String artistId;
    private String artistName;
    private String artistSlug;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
