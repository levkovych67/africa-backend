package com.africe.backend.common.dto;

import com.africe.backend.common.model.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull OrderStatus status,
        String trackingNumber
) {}
