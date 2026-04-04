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
@Document(collection = "admin_users")
public class AdminUser {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String password;
    private String name;

    @CreatedDate
    private Instant createdAt;
}
