package com.africe.backend.seed;

import com.africe.backend.common.model.Artist;
import com.africe.backend.common.model.Product;
import com.africe.backend.common.model.ProductAttribute;
import com.africe.backend.common.model.ProductStatus;
import com.africe.backend.common.model.ProductVariant;
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
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class ProductSeeder implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;
    private final S3UploadService s3UploadService;
    private final ObjectMapper objectMapper;

    @Value("${seed.enabled:true}")
    private boolean seedEnabled;

    @Value("${seed.products-path:}")
    private String productsPath;

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("Product seeding is disabled");
            return;
        }

        long productCount = mongoTemplate.getCollection("products").countDocuments();
        if (productCount > 0) {
            log.info("Products collection already has {} documents, skipping seed", productCount);
            return;
        }

        try {
            List<JsonNode> seedProducts = loadSeedData();
            log.info("Seeding {} products...", seedProducts.size());

            for (JsonNode seedProduct : seedProducts) {
                seedSingleProduct(seedProduct);
            }

            log.info("Product seeding complete!");
        } catch (Exception e) {
            log.error("Failed to seed products: {}", e.getMessage(), e);
        }
    }

    private List<JsonNode> loadSeedData() throws IOException {
        InputStream is = new ClassPathResource("seed/products-seed.json").getInputStream();
        return objectMapper.readValue(is, new TypeReference<>() {});
    }

    private void seedSingleProduct(JsonNode seed) {
        try {
            String folderName = seed.get("folderName").asText();
            String title = seed.get("title").asText();

            // Upload images
            List<String> imageUrls = uploadProductImages(folderName);

            // Parse attributes
            List<ProductAttribute> attributes = new ArrayList<>();
            if (seed.has("attributes")) {
                for (JsonNode attrNode : seed.get("attributes")) {
                    List<String> values = new ArrayList<>();
                    attrNode.get("values").forEach(v -> values.add(v.asText()));
                    attributes.add(ProductAttribute.builder()
                            .type(attrNode.get("type").asText())
                            .values(values)
                            .build());
                }
            }

            // Parse variants
            List<ProductVariant> variants = new ArrayList<>();
            if (seed.has("variants")) {
                for (JsonNode varNode : seed.get("variants")) {
                    Map<String, String> varAttrs = new HashMap<>();
                    if (varNode.has("attributes")) {
                        varNode.get("attributes").fields().forEachRemaining(
                                entry -> varAttrs.put(entry.getKey(), entry.getValue().asText()));
                    }
                    variants.add(ProductVariant.builder()
                            .sku(varNode.get("sku").asText())
                            .attributes(varAttrs)
                            .priceModifier(BigDecimal.valueOf(varNode.get("priceModifier").asDouble()))
                            .stock(varNode.get("stock").asInt())
                            .build());
                }
            }

            // Generate slug
            String slug = SlugUtils.toSlug(title) + "-" + folderName;

            // Resolve artist
            String artistId = null;
            if (seed.has("artist")) {
                String artistName = seed.get("artist").asText();
                Artist artist = mongoTemplate.findOne(
                        Query.query(Criteria.where("name").is(artistName)), Artist.class, "artists");
                if (artist != null) {
                    artistId = artist.getId();
                } else {
                    log.warn("Artist not found: {}", artistName);
                }
            }

            Product product = Product.builder()
                    .id(UUID.randomUUID().toString())
                    .slug(slug)
                    .title(title)
                    .description(seed.get("description").asText())
                    .basePrice(BigDecimal.valueOf(seed.get("basePrice").asDouble()))
                    .attributes(attributes)
                    .variants(variants)
                    .images(imageUrls)
                    .artistId(artistId)
                    .status(ProductStatus.ACTIVE)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            mongoTemplate.save(product, "products");
            log.info("Seeded product: {} ({} images)", title, imageUrls.size());

        } catch (Exception e) {
            log.error("Failed to seed product from folder {}: {}",
                    seed.get("folderName").asText(), e.getMessage(), e);
        }
    }

    private List<String> uploadProductImages(String folderName) {
        List<String> urls = new ArrayList<>();

        Path productDir = resolveProductDir(folderName);
        if (productDir == null || !Files.isDirectory(productDir)) {
            log.warn("Product folder not found: {}", folderName);
            return urls;
        }

        if (!s3UploadService.isAvailable()) {
            log.warn("S3 not available, skipping image upload for folder: {}", folderName);
            return urls;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(productDir)) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString().toLowerCase();
                if (fileName.equals("about")) continue;

                String contentType = detectContentType(fileName);
                if (contentType == null) continue;

                byte[] data = Files.readAllBytes(file);
                String sanitizedName = file.getFileName().toString()
                        .replaceAll("[^a-zA-Z0-9._-]", "_");
                String key = "products/" + folderName + "/" + sanitizedName;

                String url = s3UploadService.uploadFile(key, data, contentType);
                if (url != null) {
                    urls.add(url);
                }
            }
        } catch (IOException e) {
            log.error("Failed to read product images from folder {}: {}", folderName, e.getMessage());
        }

        return urls;
    }

    private Path resolveProductDir(String folderName) {
        // First check configured path
        if (productsPath != null && !productsPath.isBlank()) {
            return Paths.get(productsPath, folderName);
        }
        // Fall back to project-relative products/ folder
        Path projectDir = Paths.get(System.getProperty("user.dir")).getParent();
        if (projectDir == null) {
            projectDir = Paths.get(System.getProperty("user.dir"));
        }
        Path candidate = projectDir.resolve("products").resolve(folderName);
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        // Try current directory
        candidate = Paths.get("products", folderName);
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        return null;
    }

    private String detectContentType(String fileName) {
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".webp")) return "image/webp";
        if (fileName.endsWith(".mp4")) return "video/mp4";
        return null;
    }
}
