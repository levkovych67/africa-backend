package com.africe.backend.common.dto;

public record ShippingDetailsResponse(
        String city,
        String cityRef,
        String warehouseRef,
        String warehouseDescription,
        String trackingNumber,
        String carrier
) {}
