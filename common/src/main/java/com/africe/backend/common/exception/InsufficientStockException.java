package com.africe.backend.common.exception;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String sku) {
        super("Недостатньо товару на складі для SKU: " + sku);
    }
}
