package com.africe.backend.telegram.client;

import com.africe.backend.telegram.config.TelegramProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

@Slf4j
@Service
public class TelegramClient {

    private final RestClient restClient;

    public TelegramClient(TelegramProperties telegramProperties) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.telegram.org/bot" + telegramProperties.getToken())
                .build();
    }

    @CircuitBreaker(name = "telegram", fallbackMethod = "sendMessageFallback")
    public void sendMessage(String chatId, String text) {
        restClient.post()
                .uri("/sendMessage")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "chat_id", chatId,
                        "text", text,
                        "parse_mode", "HTML"
                ))
                .retrieve()
                .toBodilessEntity();
    }

    public JsonNode getUpdates(long offset) {
        try {
            return restClient.get()
                    .uri("/getUpdates?offset={offset}&timeout=1", offset)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            log.error("Failed to get Telegram updates: {}", e.getMessage());
            return null;
        }
    }

    private void sendMessageFallback(String chatId, String text, Throwable t) {
        log.error("Telegram circuit breaker open, message not sent: {}", text, t);
    }
}
