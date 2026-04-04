package com.africe.backend.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "outbox_events")
public class OutboxEvent {

    @Id
    private String id;

    @Indexed
    private OutboxChannel channel;

    private String type;
    private String payload;

    @Indexed
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    @Builder.Default
    private int retryCount = 0;

    @CreatedDate
    private Instant createdAt;

    private Instant processedAt;
}
