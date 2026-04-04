package com.africe.backend.product.controller;

import com.africe.backend.common.dto.ArtistResponse;
import com.africe.backend.product.service.ArtistService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/artists")
public class ArtistController {

    private final ArtistService artistService;

    public ArtistController(ArtistService artistService) {
        this.artistService = artistService;
    }

    @GetMapping
    public List<ArtistResponse> listArtists() {
        return artistService.listAll();
    }

    @GetMapping("/{slug}")
    public ArtistResponse getArtist(@PathVariable String slug) {
        return artistService.getBySlug(slug);
    }
}
