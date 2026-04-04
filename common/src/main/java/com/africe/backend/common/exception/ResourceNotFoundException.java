package com.africe.backend.common.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resource, String field, String value) {
        super("%s not found with %s: %s".formatted(resource, field, value));
    }
}
