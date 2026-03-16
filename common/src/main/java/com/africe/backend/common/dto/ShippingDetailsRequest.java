package com.africe.backend.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingDetailsRequest {

    @NotBlank
    private String city;

    @NotBlank
    private String cityRef;

    @NotBlank
    private String warehouseRef;

    @NotBlank
    private String warehouseDescription;
}
