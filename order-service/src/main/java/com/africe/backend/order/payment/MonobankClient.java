package com.africe.backend.order.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Service
public class MonobankClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final MonobankProperties properties;

    public MonobankClient(MonobankProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.monobank.ua/api/merchant")
                .defaultHeader("X-Token", properties.getToken())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @CircuitBreaker(name = "monobank", fallbackMethod = "createInvoiceFallback")
    public InvoiceResponse createInvoice(BigDecimal amount, String orderId, String redirectUrl) {
        // Monobank expects amount in kopiykas (cents)
        long amountInKopiykas = amount.multiply(BigDecimal.valueOf(100)).longValue();
        String webhookUrl = properties.getWebhookBaseUrl() + "/api/v1/payments/callback";

        Map<String, Object> body = Map.of(
                "amount", amountInKopiykas,
                "ccy", 980, // UAH currency code
                "merchantPaymInfo", Map.of(
                        "reference", orderId,
                        "destination", "Order #" + orderId
                ),
                "redirectUrl", redirectUrl,
                "webHookUrl", webhookUrl
        );

        try {
            String json = restClient.post()
                    .uri("/invoice/create")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(json);
            return new InvoiceResponse(
                    root.path("invoiceId").asText(),
                    root.path("pageUrl").asText()
            );
        } catch (Exception e) {
            log.error("Failed to create Monobank invoice for order {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Failed to create payment invoice", e);
        }
    }

    public String getPublicKey() {
        try {
            String json = restClient.get()
                    .uri("/pubkey")
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(json);
            return root.path("key").asText();
        } catch (Exception e) {
            log.error("Failed to fetch Monobank public key: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch public key", e);
        }
    }

    private InvoiceResponse createInvoiceFallback(BigDecimal amount, String orderId,
                                                   String redirectUrl, Throwable t) {
        log.warn("Monobank circuit breaker open: {}", t.getMessage());
        throw new RuntimeException("Payment service unavailable", t);
    }

    public record InvoiceResponse(String invoiceId, String pageUrl) {}
}
