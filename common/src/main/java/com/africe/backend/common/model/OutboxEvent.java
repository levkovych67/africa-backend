package com.africe.backend.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("outbox_events")
public class OutboxEvent {

    @Id
    private String id;

    private String type;
    private String payload;
    private OutboxStatus status;
    private int retryCount;
    private Instant createdAt;
    private Instant processedAt;
}
