package com.africe.backend.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingDetailsResponse {

    private String address;
    private String city;
    private String postalCode;
    private String country;
    private String trackingNumber;
    private String carrier;
}
