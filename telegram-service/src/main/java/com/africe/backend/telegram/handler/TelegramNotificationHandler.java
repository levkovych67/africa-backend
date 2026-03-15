package com.africe.backend.telegram.handler;

import com.africe.backend.telegram.client.TelegramClient;
import com.africe.backend.telegram.config.TelegramProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramNotificationHandler {

    private final TelegramClient telegramClient;
    private final TelegramProperties telegramProperties;
    private final ObjectMapper objectMapper;

    public void handleOrderCreated(String payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);

            String orderId = json.path("orderId").asText("N/A");
            String items = json.path("items").asText("N/A");
            String totalAmount = json.path("totalAmount").asText("N/A");
            String customerName = json.path("customerName").asText("N/A");

            String message = "<b>Нове замовлення!</b>\n" +
                    "<b>Замовлення:</b> " + orderId + "\n" +
                    "<b>Покупець:</b> " + customerName + "\n" +
                    "<b>Товари:</b>\n" + items.replace("\\n", "\n") + "\n" +
                    "<b>Сума:</b> " + totalAmount;

            for (String chatId : telegramProperties.getChatIds()) {
                telegramClient.sendMessage(chatId, message);
            }

            log.info("Telegram notification sent for order: {}", orderId);
        } catch (Exception e) {
            log.error("Failed to handle ORDER_CREATED payload: {}", payload, e);
            throw new RuntimeException("Failed to process order notification", e);
        }
    }
}
