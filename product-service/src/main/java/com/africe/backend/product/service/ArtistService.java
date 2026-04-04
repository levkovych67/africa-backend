package com.africe.backend.product.service;

import com.africe.backend.common.dto.ArtistResponse;
import com.africe.backend.common.exception.ResourceNotFoundException;
import com.africe.backend.common.model.Artist;
import com.africe.backend.product.repository.ArtistRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ArtistService {

    private final ArtistRepository artistRepository;

    public ArtistService(ArtistRepository artistRepository) {
        this.artistRepository = artistRepository;
    }

    @Cacheable("artists")
    public List<ArtistResponse> listAll() {
        return artistRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Cacheable(value = "artistBySlug", key = "#slug")
    public ArtistResponse getBySlug(String slug) {
        Artist artist = artistRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Artist", "slug", slug));
        return toResponse(artist);
    }

    public ArtistResponse toResponse(Artist artist) {
        return new ArtistResponse(
                artist.getId(),
                artist.getSlug(),
                artist.getName(),
                artist.getBio(),
                artist.getImage(),
                artist.getSocialLinks(),
                artist.getCreatedAt(),
                artist.getUpdatedAt()
        );
    }
}
