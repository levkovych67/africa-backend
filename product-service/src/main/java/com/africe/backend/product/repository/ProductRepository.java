package com.africe.backend.product.repository;

import com.africe.backend.common.model.Product;
import com.africe.backend.common.model.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ProductRepository extends MongoRepository<Product, String> {

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    Page<Product> findByStatusAndTitleContainingIgnoreCase(ProductStatus status, String title, Pageable pageable);

    Page<Product> findByStatusAndArtistId(ProductStatus status, String artistId, Pageable pageable);

    Page<Product> findByStatusAndTitleContainingIgnoreCaseAndArtistId(
            ProductStatus status, String title, String artistId, Pageable pageable);

    Optional<Product> findBySlug(String slug);

    Page<Product> findByStatusIn(java.util.List<ProductStatus> statuses, Pageable pageable);

    Page<Product> findByTitleContainingIgnoreCase(String title, Pageable pageable);
}
