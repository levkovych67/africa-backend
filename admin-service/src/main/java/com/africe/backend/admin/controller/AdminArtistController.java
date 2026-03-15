package com.africe.backend.admin.controller;

import com.africe.backend.common.audit.AdminAudited;
import com.africe.backend.common.dto.ArtistResponse;
import com.africe.backend.common.dto.CreateArtistRequest;
import com.africe.backend.common.dto.UpdateArtistRequest;
import com.africe.backend.common.model.Artist;
import com.africe.backend.common.util.SlugUtils;
import com.africe.backend.product.repository.ArtistRepository;
import com.africe.backend.product.service.ArtistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/artists")
@RequiredArgsConstructor
public class AdminArtistController {

    private final ArtistRepository artistRepository;
    private final ArtistService artistService;

    @GetMapping
    public Page<ArtistResponse> listAll(Pageable pageable) {
        return artistRepository.findAll(pageable)
                .map(this::toArtistResponse);
    }

    @PostMapping
    @AdminAudited(action = "CREATE_ARTIST")
    public ResponseEntity<ArtistResponse> create(@Valid @RequestBody CreateArtistRequest request) {
        Artist artist = Artist.builder()
                .id(UUID.randomUUID().toString())
                .slug(SlugUtils.toSlug(request.getName()))
                .name(request.getName())
                .bio(request.getBio())
                .image(request.getImage())
                .socialLinks(request.getSocialLinks())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        artist = artistService.save(artist);
        return ResponseEntity.status(HttpStatus.CREATED).body(toArtistResponse(artist));
    }

    @PutMapping("/{id}")
    @AdminAudited(action = "UPDATE_ARTIST")
    public ArtistResponse update(@PathVariable String id, @RequestBody UpdateArtistRequest request) {
        Artist artist = artistService.findById(id);

        if (request.getName() != null) {
            artist.setName(request.getName());
            artist.setSlug(SlugUtils.toSlug(request.getName()));
        }
        if (request.getBio() != null) {
            artist.setBio(request.getBio());
        }
        if (request.getImage() != null) {
            artist.setImage(request.getImage());
        }
        if (request.getSocialLinks() != null) {
            artist.setSocialLinks(request.getSocialLinks());
        }

        artist.setUpdatedAt(Instant.now());
        artist = artistService.save(artist);
        return toArtistResponse(artist);
    }

    @DeleteMapping("/{id}")
    @AdminAudited(action = "DELETE_ARTIST")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        artistService.findById(id);
        artistRepository.deleteById(id);
        return ResponseEntity.noContent().build();
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
