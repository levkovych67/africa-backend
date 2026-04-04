package com.africe.backend.product.controller;

import com.africe.backend.common.dto.ProductFiltersResponse;
import com.africe.backend.common.dto.ProductResponse;
import com.africe.backend.product.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public Page<ProductResponse> listProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String artistId,
            @RequestParam(required = false, defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return productService.listActiveProducts(search, artistId, sort, page, size);
    }

    @GetMapping("/{slug}")
    public ProductResponse getProduct(@PathVariable String slug) {
        return productService.getBySlug(slug);
    }

    @GetMapping("/filters")
    public ProductFiltersResponse getFilters() {
        return productService.getAvailableFilters();
    }
}
