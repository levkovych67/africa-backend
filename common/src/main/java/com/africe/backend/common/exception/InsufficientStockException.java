package com.africe.backend.common.exception;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String sku) {
        super("Insufficient stock for SKU: " + sku);
    }

    public InsufficientStockException(String sku, int requested, int available) {
        super("Insufficient stock for SKU %s: requested %d, available %d".formatted(sku, requested, available));
    }
}
