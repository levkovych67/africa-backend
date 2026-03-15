package com.africe.backend.product.repository;

import com.africe.backend.common.model.Artist;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArtistRepository extends MongoRepository<Artist, String> {

    Optional<Artist> findBySlug(String slug);
}
