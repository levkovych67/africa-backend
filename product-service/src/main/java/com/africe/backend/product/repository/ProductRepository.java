package com.africe.backend.product.repository;

import com.africe.backend.common.model.Product;
import com.africe.backend.common.model.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    Optional<Product> findBySlug(String slug);

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    Page<Product> findByStatusAndTitleContainingIgnoreCase(ProductStatus status, String title, Pageable pageable);

    Page<Product> findByStatusAndArtistId(ProductStatus status, String artistId, Pageable pageable);

    Page<Product> findByTitleContainingIgnoreCase(String title, Pageable pageable);
}
