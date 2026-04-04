package com.africe.backend.common.dto;

import com.africe.backend.common.model.OrderStatus;
import com.africe.backend.common.model.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String id,
        String firstName,
        String lastName,
        String email,
        String phone,
        List<OrderItemResponse> items,
        BigDecimal totalAmount,
        OrderStatus status,
        PaymentMethod paymentMethod,
        ShippingDetailsResponse shippingDetails,
        String comment,
        String accessToken,
        Instant createdAt,
        Instant updatedAt
) {}
