package com.africe.backend.common.dto;

import java.util.List;

public record ProductAttributeDto(
        String type,
        List<String> values
) {}
