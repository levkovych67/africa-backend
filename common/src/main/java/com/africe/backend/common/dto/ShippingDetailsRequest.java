package com.africe.backend.common.dto;

import jakarta.validation.constraints.NotBlank;

public record ShippingDetailsRequest(
        @NotBlank String city,
        @NotBlank String cityRef,
        @NotBlank String warehouseRef,
        @NotBlank String warehouseDescription
) {}
