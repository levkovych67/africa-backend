package com.africe.backend.admin.controller;

import com.africe.backend.common.audit.AdminAudited;
import com.africe.backend.common.dto.ArtistResponse;
import com.africe.backend.common.exception.ResourceNotFoundException;
import com.africe.backend.common.model.Artist;
import com.africe.backend.product.repository.ArtistRepository;
import com.africe.backend.product.service.ArtistService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/artists")
public class AdminArtistController {

    private final ArtistRepository artistRepository;
    private final ArtistService artistService;

    public AdminArtistController(ArtistRepository artistRepository, ArtistService artistService) {
        this.artistRepository = artistRepository;
        this.artistService = artistService;
    }

    @GetMapping
    public List<ArtistResponse> listArtists() {
        return artistRepository.findAll().stream()
                .map(artistService::toResponse)
                .toList();
    }

    @PostMapping
    @AdminAudited(action = "CREATE_ARTIST")
    @CacheEvict(value = {"artists", "artistBySlug", "productFilters"}, allEntries = true)
    public ArtistResponse createArtist(@RequestBody Artist artist) {
        artist.setId(null);
        return artistService.toResponse(artistRepository.save(artist));
    }

    @PutMapping("/{id}")
    @AdminAudited(action = "UPDATE_ARTIST")
    @CacheEvict(value = {"artists", "artistBySlug", "productFilters"}, allEntries = true)
    public ArtistResponse updateArtist(@PathVariable String id, @RequestBody Artist artist) {
        Artist existing = artistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Artist", "id", id));
        artist.setId(existing.getId());
        artist.setCreatedAt(existing.getCreatedAt());
        return artistService.toResponse(artistRepository.save(artist));
    }

    @DeleteMapping("/{id}")
    @AdminAudited(action = "DELETE_ARTIST")
    @CacheEvict(value = {"artists", "artistBySlug", "productFilters"}, allEntries = true)
    public void deleteArtist(@PathVariable String id) {
        if (!artistRepository.existsById(id)) {
            throw new ResourceNotFoundException("Artist", "id", id);
        }
        artistRepository.deleteById(id);
    }
}
