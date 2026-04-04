package com.africe.backend.admin.controller;

import com.africe.backend.common.audit.AdminAudited;
import com.africe.backend.common.dto.ArtistResponse;
import com.africe.backend.common.exception.ResourceNotFoundException;
import com.africe.backend.common.model.Artist;
import com.africe.backend.product.repository.ArtistRepository;
import com.africe.backend.product.service.ArtistService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.ResponseStatus;

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
    public Page<ArtistResponse> listArtists(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return artistRepository.findAll(pageable).map(artistService::toResponse);
    }

    @GetMapping("/{id}")
    public ArtistResponse getArtist(@PathVariable String id) {
        Artist artist = artistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Artist", "id", id));
        return artistService.toResponse(artist);
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
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @AdminAudited(action = "DELETE_ARTIST")
    @CacheEvict(value = {"artists", "artistBySlug", "productFilters"}, allEntries = true)
    public void deleteArtist(@PathVariable String id) {
        if (!artistRepository.existsById(id)) {
            throw new ResourceNotFoundException("Artist", "id", id);
        }
        artistRepository.deleteById(id);
    }
}
