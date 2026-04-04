package com.africe.backend.common.dto;

import jakarta.validation.constraints.NotBlank;

public record PresignRequest(
        @NotBlank String fileName,
        @NotBlank String contentType
) {}
