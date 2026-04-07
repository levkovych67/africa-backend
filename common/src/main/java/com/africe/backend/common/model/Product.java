package com.africe.backend.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "products")
public class Product {

    @Id
    private String id;

    @Indexed(unique = true)
    private String slug;

    @NotBlank(message = "Title is required")
    private String title;
    private String description;

    private List<ProductAttribute> attributes;
    @jakarta.validation.Valid
    private List<ProductVariant> variants;
    private List<String> images;

    @Indexed
    private BigDecimal minPrice;

    @Indexed
    private String artistId;

    @Builder.Default
    private ProductStatus status = ProductStatus.DRAFT;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
