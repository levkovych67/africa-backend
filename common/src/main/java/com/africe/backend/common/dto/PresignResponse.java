package com.africe.backend.common.dto;

public record PresignResponse(
        String uploadUrl,
        String publicUrl
) {}
