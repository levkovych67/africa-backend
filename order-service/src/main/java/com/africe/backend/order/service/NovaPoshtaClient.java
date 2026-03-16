package com.africe.backend.order.service;

import com.africe.backend.common.dto.NovaCityResponse;
import com.africe.backend.common.dto.NovaWarehouseResponse;
import com.africe.backend.order.config.NovaPoshtaProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NovaPoshtaClient {

    private final NovaPoshtaProperties properties;
    private final ObjectMapper objectMapper;

    public List<NovaCityResponse> searchCities(String query, int limit) {
        Map<String, Object> body = Map.of(
                "apiKey", properties.getApiKey(),
                "modelName", "Address",
                "calledMethod", "searchSettlements",
                "methodProperties", Map.of(
                        "CityName", query,
                        "Limit", String.valueOf(limit)
                )
        );

        try {
            String json = RestClient.create()
                    .post()
                    .uri(properties.getBaseUrl())
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(json);
            if (!root.path("success").asBoolean(false)) {
                log.warn("Nova Poshta searchSettlements failed: {}", root.path("errors"));
                return Collections.emptyList();
            }

            List<NovaCityResponse> cities = new ArrayList<>();
            JsonNode data = root.path("data");
            for (JsonNode item : data) {
                JsonNode addresses = item.path("Addresses");
                for (JsonNode addr : addresses) {
                    cities.add(NovaCityResponse.builder()
                            .ref(addr.path("Ref").asText())
                            .name(addr.path("MainDescription").asText())
                            .region(addr.path("Area").asText())
                            .build());
                }
            }
            return cities;
        } catch (Exception e) {
            log.error("Nova Poshta searchSettlements error: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    public List<NovaWarehouseResponse> getWarehouses(String cityRef, int limit) {
        Map<String, Object> body = Map.of(
                "apiKey", properties.getApiKey(),
                "modelName", "Address",
                "calledMethod", "getWarehouses",
                "methodProperties", Map.of(
                        "CityRef", cityRef,
                        "Limit", String.valueOf(limit)
                )
        );

        try {
            String json = RestClient.create()
                    .post()
                    .uri(properties.getBaseUrl())
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(json);
            if (!root.path("success").asBoolean(false)) {
                log.warn("Nova Poshta getWarehouses failed: {}", root.path("errors"));
                return Collections.emptyList();
            }

            List<NovaWarehouseResponse> warehouses = new ArrayList<>();
            for (JsonNode item : root.path("data")) {
                warehouses.add(NovaWarehouseResponse.builder()
                        .ref(item.path("Ref").asText())
                        .description(item.path("Description").asText())
                        .number(item.path("Number").asText())
                        .shortAddress(item.path("ShortAddress").asText())
                        .build());
            }
            return warehouses;
        } catch (Exception e) {
            log.error("Nova Poshta getWarehouses error: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}
