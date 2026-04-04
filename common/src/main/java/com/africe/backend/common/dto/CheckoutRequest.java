package com.africe.backend.common.dto;

import com.africe.backend.common.model.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CheckoutRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Email String email,
        @NotBlank String phone,
        @NotEmpty @Valid List<CheckoutItemRequest> items,
        @NotNull @Valid ShippingDetailsRequest shippingDetails,
        String comment,
        @NotNull PaymentMethod paymentMethod
) {}
