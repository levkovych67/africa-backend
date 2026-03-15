package com.africe.backend.product.controller;

import com.africe.backend.common.dto.ArtistResponse;
import com.africe.backend.common.dto.ProductResponse;
import com.africe.backend.product.service.ArtistService;
import com.africe.backend.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/artists")
@RequiredArgsConstructor
public class ArtistController {

    private final ArtistService artistService;
    private final ProductService productService;

    @GetMapping
    public List<ArtistResponse> listArtists() {
        return artistService.listAllArtists();
    }

    @GetMapping("/{slug}")
    public ArtistResponse getBySlug(@PathVariable String slug) {
        return artistService.getBySlug(slug);
    }

    @GetMapping("/{slug}/products")
    public Page<ProductResponse> getArtistProducts(@PathVariable String slug, Pageable pageable) {
        ArtistResponse artist = artistService.getBySlug(slug);
        return productService.listActiveProductsByArtist(artist.getId(), pageable);
    }
}
