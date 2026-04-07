package com.africe.backend.order.service;

import com.africe.backend.common.dto.NovaCityResponse;
import com.africe.backend.common.dto.NovaWarehouseResponse;
import com.africe.backend.order.config.NovaPoshtaProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class NovaPoshtaClient {

    private final NovaPoshtaProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public NovaPoshtaClient(NovaPoshtaProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Cacheable(value = "novaPoshtaCities", key = "#query + '-' + #limit")
    @CircuitBreaker(name = "novaPoshta", fallbackMethod = "searchCitiesFallback")
    public List<NovaCityResponse> searchCities(String query, int limit) {
        Map<String, Object> body = Map.of(
                "apiKey", properties.getApiKey(),
                "modelName", "Address",
                "calledMethod", "getCities",
                "methodProperties", Map.of(
                        "FindByString", query,
                        "Limit", String.valueOf(limit)
                )
        );

        String json = restClient.post()
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.path("success").asBoolean(false)) {
                log.warn("Nova Poshta getCities failed: {}", root.path("errors"));
                return Collections.emptyList();
            }

            List<NovaCityResponse> cities = new ArrayList<>();
            JsonNode data = root.path("data");
            for (JsonNode item : data) {
                cities.add(NovaCityResponse.builder()
                        .ref(item.path("Ref").asText())
                        .name(item.path("Description").asText())
                        .region(item.path("AreaDescription").asText())
                        .build());
            }
            return cities;
        } catch (Exception e) {
            log.error("Nova Poshta getCities parse error: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Cacheable(value = "novaPoshtaWarehouses", key = "#cityRef")
    @CircuitBreaker(name = "novaPoshta", fallbackMethod = "getWarehousesFallback")
    public List<NovaWarehouseResponse> getWarehouses(String cityRef) {
        try {
            Map<String, Object> body = Map.of(
                    "apiKey", properties.getApiKey(),
                    "modelName", "Address",
                    "calledMethod", "getWarehouses",
                    "methodProperties", Map.of(
                            "CityRef", cityRef,
                            "Limit", "3000"
                    )
            );

            String json = restClient.post()
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(json);
            if (!root.path("success").asBoolean(false)) {
                log.warn("Nova Poshta getWarehouses failed: {}", root.path("errors"));
                return Collections.emptyList();
            }

            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                return Collections.emptyList();
            }

            List<NovaWarehouseResponse> warehouses = new ArrayList<>();
            for (JsonNode item : data) {
                String description = item.path("Description").asText();
                boolean isPostbox = description.toLowerCase().contains("поштомат");
                warehouses.add(NovaWarehouseResponse.builder()
                        .ref(item.path("Ref").asText())
                        .description(description)
                        .number(item.path("Number").asText())
                        .shortAddress(item.path("ShortAddress").asText())
                        .type(isPostbox ? "postbox" : "warehouse")
                        .build());
            }

            log.info("Loaded {} warehouses for cityRef {}", warehouses.size(), cityRef);

            warehouses.sort(Comparator
                    .comparing((NovaWarehouseResponse w) -> "postbox".equals(w.getType()))
                    .thenComparing(w -> {
                        try { return Integer.parseInt(w.getNumber()); }
                        catch (NumberFormatException e) { return Integer.MAX_VALUE; }
                    }));

            return warehouses;
        } catch (Exception e) {
            log.error("Nova Poshta getWarehouses parse error: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<NovaCityResponse> searchCitiesFallback(String query, int limit, Throwable t) {
        log.warn("Nova Poshta circuit breaker open for searchCities: {}", t.getMessage());
        return Collections.emptyList();
    }

    private List<NovaWarehouseResponse> getWarehousesFallback(String cityRef, Throwable t) {
        log.warn("Nova Poshta circuit breaker open for getWarehouses: {}", t.getMessage());
        return Collections.emptyList();
    }
}
