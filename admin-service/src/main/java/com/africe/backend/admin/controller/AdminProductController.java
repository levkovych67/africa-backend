package com.africe.backend.admin.controller;

import com.africe.backend.common.audit.AdminAudited;
import com.africe.backend.common.dto.ProductResponse;
import com.africe.backend.common.exception.ResourceNotFoundException;
import com.africe.backend.common.model.Product;
import com.africe.backend.common.model.ProductStatus;
import com.africe.backend.product.repository.ProductRepository;
import com.africe.backend.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.text.Normalizer;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/products")
public class AdminProductController {

    private final ProductRepository productRepository;
    private final ProductService productService;

    public AdminProductController(ProductRepository productRepository,
                                  ProductService productService) {
        this.productRepository = productRepository;
        this.productService = productService;
    }

    @GetMapping
    public Page<ProductResponse> listProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Product> products;
        if (search != null && !search.isBlank()) {
            products = productRepository.findByTitleContainingIgnoreCase(search, pageable);
        } else if (status != null) {
            products = productRepository.findByStatus(status, pageable);
        } else {
            products = productRepository.findAll(pageable);
        }
        return products.map(productService::toResponse);
    }

    @GetMapping("/{id}")
    public ProductResponse getProduct(@PathVariable String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        return productService.toResponse(product);
    }

    @PostMapping
    @AdminAudited(action = "CREATE_PRODUCT")
    @CacheEvict(value = {"products", "productBySlug", "productFilters"}, allEntries = true)
    public ProductResponse createProduct(@Valid @RequestBody Product product) {
        product.setId(null);
        if (product.getStatus() == null) {
            product.setStatus(ProductStatus.DRAFT);
        }
        if (product.getSlug() == null || product.getSlug().isBlank()) {
            product.setSlug(generateSlug(product.getTitle()));
        }
        return productService.toResponse(productRepository.save(product));
    }

    private String generateSlug(String title) {
        if (title == null || title.isBlank()) return UUID.randomUUID().toString();
        String transliterated = transliterate(title.toLowerCase());
        String slug = transliterated
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("[\\s]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (slug.isEmpty()) return UUID.randomUUID().toString();
        return slug + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static final String[][] UA_TRANSLIT = {
            {"зг", "zgh"}, {"ьо", "io"}, {"ьї", "ii"},
            {"а", "a"}, {"б", "b"}, {"в", "v"}, {"г", "h"}, {"ґ", "g"},
            {"д", "d"}, {"е", "e"}, {"є", "ye"}, {"ж", "zh"}, {"з", "z"},
            {"и", "y"}, {"і", "i"}, {"ї", "yi"}, {"й", "i"}, {"к", "k"},
            {"л", "l"}, {"м", "m"}, {"н", "n"}, {"о", "o"}, {"п", "p"},
            {"р", "r"}, {"с", "s"}, {"т", "t"}, {"у", "u"}, {"ф", "f"},
            {"х", "kh"}, {"ц", "ts"}, {"ч", "ch"}, {"ш", "sh"}, {"щ", "shch"},
            {"ь", ""}, {"ю", "yu"}, {"я", "ya"}, {"'", ""},
            {"ъ", ""}, {"ы", "y"}, {"э", "e"},
    };

    private static String transliterate(String input) {
        String result = input;
        for (String[] pair : UA_TRANSLIT) {
            result = result.replace(pair[0], pair[1]);
        }
        return result;
    }

    @PutMapping("/{id}")
    @AdminAudited(action = "UPDATE_PRODUCT")
    @CacheEvict(value = {"products", "productBySlug", "productFilters"}, allEntries = true)
    public ProductResponse updateProduct(@PathVariable String id, @Valid @RequestBody Product product) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        product.setId(existing.getId());
        product.setCreatedAt(existing.getCreatedAt());
        if (product.getSlug() == null) product.setSlug(existing.getSlug());
        if (product.getStatus() == null) product.setStatus(existing.getStatus());
        if (product.getArtistId() == null) product.setArtistId(existing.getArtistId());
        return productService.toResponse(productRepository.save(product));
    }

    @DeleteMapping("/{id}")
    @AdminAudited(action = "ARCHIVE_PRODUCT")
    @CacheEvict(value = {"products", "productBySlug", "productFilters"}, allEntries = true)
    public ProductResponse archiveProduct(@PathVariable String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        product.setStatus(ProductStatus.ARCHIVED);
        return productService.toResponse(productRepository.save(product));
    }
}
