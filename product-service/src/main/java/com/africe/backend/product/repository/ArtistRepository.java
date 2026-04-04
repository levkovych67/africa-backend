package com.africe.backend.product.repository;

import com.africe.backend.common.model.Artist;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ArtistRepository extends MongoRepository<Artist, String> {

    Optional<Artist> findBySlug(String slug);
}
