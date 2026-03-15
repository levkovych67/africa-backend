package com.africe.backend.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("audit_logs")
public class AuditLog {

    @Id
    private String id;

    private String adminId;
    private String adminEmail;
    private String action;
    private String targetType;
    private String targetId;
    private String details;

    @Indexed
    private Instant createdAt;
}
