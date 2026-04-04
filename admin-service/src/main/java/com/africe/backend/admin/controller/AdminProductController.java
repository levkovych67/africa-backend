package com.africe.backend.admin.controller;

import com.africe.backend.common.audit.AdminAudited;
import com.africe.backend.common.dto.ProductResponse;
import com.africe.backend.common.exception.ResourceNotFoundException;
import com.africe.backend.common.model.Product;
import com.africe.backend.common.model.ProductStatus;
import com.africe.backend.product.repository.ProductRepository;
import com.africe.backend.product.service.ProductService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping
    @AdminAudited(action = "CREATE_PRODUCT")
    @CacheEvict(value = {"products", "productBySlug", "productFilters"}, allEntries = true)
    public ProductResponse createProduct(@RequestBody Product product) {
        product.setId(null);
        if (product.getStatus() == null) {
            product.setStatus(ProductStatus.DRAFT);
        }
        return productService.toResponse(productRepository.save(product));
    }

    @PutMapping("/{id}")
    @AdminAudited(action = "UPDATE_PRODUCT")
    @CacheEvict(value = {"products", "productBySlug", "productFilters"}, allEntries = true)
    public ProductResponse updateProduct(@PathVariable String id, @RequestBody Product product) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        product.setId(existing.getId());
        product.setCreatedAt(existing.getCreatedAt());
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
