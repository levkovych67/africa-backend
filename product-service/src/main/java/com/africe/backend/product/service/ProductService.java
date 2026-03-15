package com.africe.backend.product.service;

import com.africe.backend.common.dto.ProductAttributeDto;
import com.africe.backend.common.dto.ProductResponse;
import com.africe.backend.common.dto.ProductVariantDto;
import com.africe.backend.common.exception.ResourceNotFoundException;
import com.africe.backend.common.model.Product;
import com.africe.backend.common.model.ProductAttribute;
import com.africe.backend.common.model.Artist;
import com.africe.backend.common.model.ProductStatus;
import com.africe.backend.common.model.ProductVariant;
import com.africe.backend.product.repository.ArtistRepository;
import com.africe.backend.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ArtistRepository artistRepository;

    public Page<ProductResponse> listActiveProducts(String search, Pageable pageable) {
        Page<Product> products;
        if (search == null || search.isBlank()) {
            products = productRepository.findByStatus(ProductStatus.ACTIVE, pageable);
        } else {
            products = productRepository.findByStatusAndTitleContainingIgnoreCase(
                    ProductStatus.ACTIVE, search, pageable);
        }
        return products.map(this::toProductResponse);
    }

    public ProductResponse getBySlug(String slug) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with slug: " + slug));
        return toProductResponse(product);
    }

    public Product findById(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    public Page<ProductResponse> listActiveProductsByArtist(String artistId, Pageable pageable) {
        return productRepository.findByStatusAndArtistId(ProductStatus.ACTIVE, artistId, pageable)
                .map(this::toProductResponse);
    }

    public Product save(Product product) {
        return productRepository.save(product);
    }

    private ProductResponse toProductResponse(Product product) {
        String artistName = null;
        String artistSlug = null;
        if (product.getArtistId() != null) {
            Artist artist = artistRepository.findById(product.getArtistId()).orElse(null);
            if (artist != null) {
                artistName = artist.getName();
                artistSlug = artist.getSlug();
            }
        }

        return ProductResponse.builder()
                .id(product.getId())
                .slug(product.getSlug())
                .title(product.getTitle())
                .description(product.getDescription())
                .basePrice(product.getBasePrice())
                .attributes(mapAttributes(product.getAttributes()))
                .variants(mapVariants(product.getVariants()))
                .images(product.getImages())
                .artistId(product.getArtistId())
                .artistName(artistName)
                .artistSlug(artistSlug)
                .status(product.getStatus() != null ? product.getStatus().name() : null)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    private List<ProductAttributeDto> mapAttributes(List<ProductAttribute> attributes) {
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

    private List<ProductVariantDto> mapVariants(List<ProductVariant> variants) {
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
}
