package com.africe.backend.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtistResponse {

    private String id;
    private String slug;
    private String name;
    private String bio;
    private String image;
    private Map<String, String> socialLinks;
    private Instant createdAt;
    private Instant updatedAt;
}
