package com.africe.backend.common.exception;

public class PaymentRequiredException extends RuntimeException {

    public PaymentRequiredException(String orderId) {
        super("Payment required for order: " + orderId);
    }
}
