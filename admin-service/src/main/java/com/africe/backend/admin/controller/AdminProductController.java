package com.africe.backend.admin.controller;

import com.africe.backend.common.audit.AdminAudited;
import com.africe.backend.common.dto.CreateProductRequest;
import com.africe.backend.common.dto.ProductAttributeDto;
import com.africe.backend.common.dto.ProductResponse;
import com.africe.backend.common.dto.ProductVariantDto;
import com.africe.backend.common.dto.UpdateProductRequest;
import com.africe.backend.common.model.Product;
import com.africe.backend.common.model.ProductAttribute;
import com.africe.backend.common.model.ProductStatus;
import com.africe.backend.common.model.ProductVariant;
import com.africe.backend.common.util.SlugUtils;
import com.africe.backend.product.repository.ProductRepository;
import com.africe.backend.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductRepository productRepository;
    private final ProductService productService;

    @GetMapping
    public Page<ProductResponse> listAll(Pageable pageable) {
        return productRepository.findAll(pageable)
                .map(this::toProductResponse);
    }

    @PostMapping
    @AdminAudited(action = "CREATE_PRODUCT")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        String slug = SlugUtils.toSlug(request.getTitle());

        List<ProductAttribute> attributes = mapAttributeDtosToEntities(request.getAttributes());
        List<ProductVariant> variants = mapVariantDtosToEntities(request.getVariants());

        Product product = Product.builder()
                .title(request.getTitle())
                .slug(slug)
                .description(request.getDescription())
                .basePrice(request.getBasePrice())
                .attributes(attributes)
                .variants(variants)
                .artistId(request.getArtistId())
                .images(request.getImages())
                .status(ProductStatus.DRAFT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        product = productService.save(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(toProductResponse(product));
    }

    @PutMapping("/{id}")
    @AdminAudited(action = "UPDATE_PRODUCT")
    public ProductResponse update(@PathVariable String id,
                                  @RequestBody UpdateProductRequest request) {
        Product product = productService.findById(id);

        if (request.getTitle() != null) {
            product.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getBasePrice() != null) {
            product.setBasePrice(request.getBasePrice());
        }
        if (request.getAttributes() != null) {
            product.setAttributes(mapAttributeDtosToEntities(request.getAttributes()));
        }
        if (request.getVariants() != null) {
            product.setVariants(mapVariantDtosToEntities(request.getVariants()));
        }
        if (request.getArtistId() != null) {
            product.setArtistId(request.getArtistId());
        }
        if (request.getImages() != null) {
            product.setImages(request.getImages());
        }

        product.setUpdatedAt(Instant.now());
        product = productService.save(product);
        return toProductResponse(product);
    }

    @DeleteMapping("/{id}")
    @AdminAudited(action = "ARCHIVE_PRODUCT")
    public ResponseEntity<Void> archive(@PathVariable String id) {
        Product product = productService.findById(id);
        product.setStatus(ProductStatus.ARCHIVED);
        product.setUpdatedAt(Instant.now());
        productService.save(product);
        return ResponseEntity.noContent().build();
    }

    private ProductResponse toProductResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .slug(product.getSlug())
                .title(product.getTitle())
                .description(product.getDescription())
                .basePrice(product.getBasePrice())
                .attributes(mapAttributesToDtos(product.getAttributes()))
                .variants(mapVariantsToDtos(product.getVariants()))
                .images(product.getImages())
                .status(product.getStatus() != null ? product.getStatus().name() : null)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    private List<ProductAttributeDto> mapAttributesToDtos(List<ProductAttribute> attributes) {
        if (attributes == null) {
            return Collections.emptyList();
        }
        return attributes.stream()
                .map(attr -> ProductAttributeDto.builder()
                        .type(attr.getType())
                        .values(attr.getValues())
                        .build())
                .collect(Collectors.toList());
    }

    private List<ProductVariantDto> mapVariantsToDtos(List<ProductVariant> variants) {
        if (variants == null) {
            return Collections.emptyList();
        }
        return variants.stream()
                .map(variant -> ProductVariantDto.builder()
                        .sku(variant.getSku())
                        .attributes(variant.getAttributes())
                        .priceModifier(variant.getPriceModifier())
                        .stock(variant.getStock())
                        .build())
                .collect(Collectors.toList());
    }

    private List<ProductAttribute> mapAttributeDtosToEntities(List<ProductAttributeDto> dtos) {
        if (dtos == null) {
            return Collections.emptyList();
        }
        return dtos.stream()
                .map(dto -> ProductAttribute.builder()
                        .type(dto.getType())
                        .values(dto.getValues())
                        .build())
                .collect(Collectors.toList());
    }

    private List<ProductVariant> mapVariantDtosToEntities(List<ProductVariantDto> dtos) {
        if (dtos == null) {
            return Collections.emptyList();
        }
        return dtos.stream()
                .map(dto -> ProductVariant.builder()
                        .sku(dto.getSku())
                        .attributes(dto.getAttributes())
                        .priceModifier(dto.getPriceModifier())
                        .stock(dto.getStock())
                        .build())
                .collect(Collectors.toList());
    }
}
