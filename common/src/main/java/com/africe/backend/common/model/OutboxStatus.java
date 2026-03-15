package com.africe.backend.common.model;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED
}
