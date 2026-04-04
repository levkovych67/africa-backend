package com.africe.backend.common.dto;

import java.util.List;

public record ProductFiltersResponse(
        List<ArtistFilterDto> artists,
        List<ProductAttributeDto> attributes
) {
    public record ArtistFilterDto(
            String id,
            String name,
            String slug
    ) {}
}
