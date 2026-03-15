package com.africe.backend.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateArtistRequest {

    @NotBlank
    private String name;

    private String bio;
    private String image;
    private Map<String, String> socialLinks;
}
