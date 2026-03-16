package com.africe.backend.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private List<OrderItemResponse> items;
    private BigDecimal totalAmount;
    private String status;
    private ShippingDetailsResponse shippingDetails;
    private String comment;
    private Instant createdAt;
    private Instant updatedAt;
}
