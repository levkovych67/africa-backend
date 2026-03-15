package com.africe.backend.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("products")
public class Product {

    @Id
    private String id;

    @Indexed(unique = true)
    private String slug;

    private String title;
    private String description;
    private BigDecimal basePrice;
    private List<ProductAttribute> attributes;
    private List<ProductVariant> variants;
    private List<String> images;
    @Indexed
    private String artistId;
    @Indexed
    private ProductStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
