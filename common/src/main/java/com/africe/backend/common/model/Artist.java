package com.africe.backend.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("artists")
public class Artist {

    @Id
    private String id;

    @Indexed(unique = true)
    private String slug;

    private String name;
    private String bio;
    private String image;
    private Map<String, String> socialLinks;
    private Instant createdAt;
    private Instant updatedAt;
}
