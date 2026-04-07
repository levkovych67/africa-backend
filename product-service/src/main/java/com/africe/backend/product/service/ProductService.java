package com.africe.backend.product.service;

import com.africe.backend.common.dto.ProductAttributeDto;
import com.africe.backend.common.dto.ProductFiltersResponse;
import com.africe.backend.common.dto.ProductResponse;
import com.africe.backend.common.dto.ProductVariantDto;
import com.africe.backend.common.exception.ResourceNotFoundException;
import com.africe.backend.common.model.Artist;
import com.africe.backend.common.model.Product;
import com.africe.backend.common.model.ProductStatus;
import com.africe.backend.product.repository.ArtistRepository;
import com.africe.backend.product.repository.ProductRepository;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ArtistRepository artistRepository;
    private final MongoTemplate mongoTemplate;

    public ProductService(ProductRepository productRepository,
                          ArtistRepository artistRepository,
                          MongoTemplate mongoTemplate) {
        this.productRepository = productRepository;
        this.artistRepository = artistRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Cacheable(value = "products", key = "#search + '-' + #artistId + '-' + #sort + '-' + #page + '-' + #size")
    public Page<ProductResponse> listActiveProducts(String search, String artistId,
                                                     String sort, int page, int size) {
        Pageable pageable = buildPageable(sort, page, size);

        Page<Product> products;
        boolean hasSearch = search != null && !search.isBlank();
        boolean hasArtist = artistId != null && !artistId.isBlank();

        if (hasSearch && hasArtist) {
            products = productRepository.findByStatusAndTitleContainingIgnoreCaseAndArtistId(
                    ProductStatus.ACTIVE, search, artistId, pageable);
        } else if (hasSearch) {
            products = productRepository.findByStatusAndTitleContainingIgnoreCase(
                    ProductStatus.ACTIVE, search, pageable);
        } else if (hasArtist) {
            products = productRepository.findByStatusAndArtistId(
                    ProductStatus.ACTIVE, artistId, pageable);
        } else {
            products = productRepository.findByStatus(ProductStatus.ACTIVE, pageable);
        }

        return products.map(this::toResponse);
    }

    @Cacheable(value = "productBySlug", key = "#slug")
    public ProductResponse getBySlug(String slug) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "slug", slug));
        return toResponse(product);
    }

    @Cacheable("productFilters")
    public ProductFiltersResponse getAvailableFilters() {
        // Get artists that have active products
        List<ProductFiltersResponse.ArtistFilterDto> artistFilters = artistRepository.findAll().stream()
                .map(a -> new ProductFiltersResponse.ArtistFilterDto(a.getId(), a.getName(), a.getSlug()))
                .toList();

        // MongoDB aggregation to get unique attribute types and values from active products
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("status").is(ProductStatus.ACTIVE.name())),
                Aggregation.unwind("attributes"),
                Aggregation.unwind("attributes.values"),
                Aggregation.group("attributes.type").addToSet("attributes.values").as("values"),
                Aggregation.project().and("_id").as("type").and("values").as("values")
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(
                aggregation, "products", Document.class);

        List<ProductAttributeDto> attributeFilters = results.getMappedResults().stream()
                .map(doc -> new ProductAttributeDto(
                        doc.getString("type"),
                        doc.getList("values", String.class)
                ))
                .toList();

        return new ProductFiltersResponse(artistFilters, attributeFilters);
    }

    public ProductResponse toResponse(Product product) {
        List<ProductVariantDto> variants = product.getVariants() != null
                ? product.getVariants().stream()
                .map(v -> new ProductVariantDto(v.getSku(), v.getAttributes(), v.getPriceModifier(), v.getStock()))
                .toList()
                : List.of();

        List<ProductAttributeDto> attributes = product.getAttributes() != null
                ? product.getAttributes().stream()
                .map(a -> new ProductAttributeDto(a.getType(), a.getValues()))
                .toList()
                : List.of();

        String artistName = null;
        if (product.getArtistId() != null) {
            artistName = artistRepository.findById(product.getArtistId())
                    .map(Artist::getName)
                    .orElse(null);
        }

        return new ProductResponse(
                product.getId(),
                product.getSlug(),
                product.getTitle(),
                product.getDescription(),
                product.getBasePrice(),
                attributes,
                variants,
                product.getImages(),
                product.getArtistId(),
                artistName,
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    private Pageable buildPageable(String sort, int page, int size) {
        Sort sorting = switch (sort != null ? sort : "") {
            case "price_asc", "basePrice,asc" -> Sort.by(Sort.Direction.ASC, "basePrice");
            case "price_desc", "basePrice,desc" -> Sort.by(Sort.Direction.DESC, "basePrice");
            case "newest", "createdAt,desc" -> Sort.by(Sort.Direction.DESC, "createdAt");
            default -> {
                // Support generic "field,direction" format
                if (sort != null && sort.contains(",")) {
                    String[] parts = sort.split(",", 2);
                    Sort.Direction dir = "asc".equalsIgnoreCase(parts[1])
                            ? Sort.Direction.ASC : Sort.Direction.DESC;
                    yield Sort.by(dir, parts[0]);
                }
                yield Sort.by(Sort.Direction.DESC, "createdAt");
            }
        };
        return PageRequest.of(page, size, sorting);
    }
}
