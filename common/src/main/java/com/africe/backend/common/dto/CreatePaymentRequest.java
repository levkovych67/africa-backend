package com.africe.backend.common.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePaymentRequest(
        @NotBlank(message = "orderId is required") String orderId,
        @NotBlank(message = "accessToken is required") String accessToken,
        String redirectUrl
) {}
