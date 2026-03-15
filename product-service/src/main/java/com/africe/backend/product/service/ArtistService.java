package com.africe.backend.product.service;

import com.africe.backend.common.dto.ArtistResponse;
import com.africe.backend.common.exception.ResourceNotFoundException;
import com.africe.backend.common.model.Artist;
import com.africe.backend.product.repository.ArtistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArtistService {

    private final ArtistRepository artistRepository;

    public List<ArtistResponse> listAllArtists() {
        return artistRepository.findAll().stream()
                .map(this::toArtistResponse)
                .collect(Collectors.toList());
    }

    public ArtistResponse getBySlug(String slug) {
        Artist artist = artistRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Artist not found with slug: " + slug));
        return toArtistResponse(artist);
    }

    public Artist findById(String id) {
        return artistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Artist not found with id: " + id));
    }

    public Artist save(Artist artist) {
        return artistRepository.save(artist);
    }

    private ArtistResponse toArtistResponse(Artist artist) {
        return ArtistResponse.builder()
                .id(artist.getId())
                .slug(artist.getSlug())
                .name(artist.getName())
                .bio(artist.getBio())
                .image(artist.getImage())
                .socialLinks(artist.getSocialLinks())
                .createdAt(artist.getCreatedAt())
                .updatedAt(artist.getUpdatedAt())
                .build();
    }
}
