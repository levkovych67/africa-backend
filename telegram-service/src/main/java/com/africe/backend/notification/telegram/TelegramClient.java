package com.africe.backend.notification.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TelegramClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public TelegramClient(@Value("${telegram.bot-token}") String botToken,
                          ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.telegram.org/bot" + botToken)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @CircuitBreaker(name = "telegram", fallbackMethod = "sendMessageFallback")
    public void sendMessage(String chatId, String text, List<List<Map<String, String>>> inlineKeyboard) {
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", "HTML");

        if (inlineKeyboard != null && !inlineKeyboard.isEmpty()) {
            body.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));
        }

        restClient.post()
                .uri("/sendMessage")
                .body(body)
                .retrieve()
                .body(String.class);
    }

    @CircuitBreaker(name = "telegram", fallbackMethod = "answerCallbackFallback")
    public void answerCallbackQuery(String callbackQueryId, String text) {
        Map<String, Object> body = Map.of(
                "callback_query_id", callbackQueryId,
                "text", text
        );
        restClient.post()
                .uri("/answerCallbackQuery")
                .body(body)
                .retrieve()
                .body(String.class);
    }

    @CircuitBreaker(name = "telegram", fallbackMethod = "editMarkupFallback")
    public void editMessageReplyMarkup(String chatId, int messageId) {
        Map<String, Object> body = Map.of(
                "chat_id", chatId,
                "message_id", messageId,
                "reply_markup", Map.of("inline_keyboard", List.of())
        );
        restClient.post()
                .uri("/editMessageReplyMarkup")
                .body(body)
                .retrieve()
                .body(String.class);
    }

    public List<JsonNode> getUpdates(long offset) {
        try {
            String json = restClient.post()
                    .uri("/getUpdates")
                    .body(Map.of("offset", offset, "timeout", 10))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(json);
            if (root.path("ok").asBoolean(false)) {
                return objectMapper.convertValue(
                        root.path("result"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, JsonNode.class));
            }
        } catch (Exception e) {
            log.warn("Failed to get Telegram updates: {}", e.getMessage());
        }
        return List.of();
    }

    private void sendMessageFallback(String chatId, String text,
                                      List<List<Map<String, String>>> keyboard, Throwable t) {
        log.warn("Telegram circuit breaker open for sendMessage: {}", t.getMessage());
        throw new RuntimeException("Telegram unavailable", t);
    }

    private void answerCallbackFallback(String callbackQueryId, String text, Throwable t) {
        log.warn("Telegram circuit breaker open for answerCallback: {}", t.getMessage());
    }

    private void editMarkupFallback(String chatId, int messageId, Throwable t) {
        log.warn("Telegram circuit breaker open for editMarkup: {}", t.getMessage());
    }
}
