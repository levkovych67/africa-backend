package com.africe.backend.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingDetails {

    private String address;
    private String city;
    private String postalCode;
    private String country;
    private String cityRef;
    private String warehouseRef;
    private String warehouseDescription;
    private String trackingNumber;
    private String carrier;
}
