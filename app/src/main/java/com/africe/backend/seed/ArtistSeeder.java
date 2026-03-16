package com.africe.backend.seed;

import com.africe.backend.common.model.Artist;
import com.africe.backend.common.util.SlugUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class ArtistSeeder implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;

    @Value("${seed.enabled:true}")
    private boolean seedEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("Artist seeding is disabled");
            return;
        }

        long artistCount = mongoTemplate.getCollection("artists").countDocuments();
        if (artistCount > 0) {
            log.info("Artists collection already has {} documents, skipping seed", artistCount);
            return;
        }

        try {
            InputStream is = new ClassPathResource("seed/artists-seed.json").getInputStream();
            List<JsonNode> seedArtists = objectMapper.readValue(is, new TypeReference<>() {});
            log.info("Seeding {} artists...", seedArtists.size());

            for (JsonNode seed : seedArtists) {
                String name = seed.get("name").asText();
                String bio = seed.has("bio") ? seed.get("bio").asText() : "";

                Artist artist = Artist.builder()
                        .id(UUID.randomUUID().toString())
                        .slug(SlugUtils.toSlug(name))
                        .name(name)
                        .bio(bio)
                        .socialLinks(Collections.emptyMap())
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();

                mongoTemplate.save(artist, "artists");
                log.info("Seeded artist: {} (slug: {})", name, artist.getSlug());
            }

            log.info("Artist seeding complete!");
        } catch (Exception e) {
            log.error("Failed to seed artists: {}", e.getMessage(), e);
        }
    }
}
