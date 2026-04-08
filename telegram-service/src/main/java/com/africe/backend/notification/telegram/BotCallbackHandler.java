package com.africe.backend.notification.telegram;

import com.africe.backend.common.model.Order;
import com.africe.backend.common.model.OrderStatus;
import com.africe.backend.order.repository.OrderRepository;
import com.africe.backend.order.service.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class BotCallbackHandler {

    private static final String OFFSET_KEY = "telegram_update_offset";

    private final TelegramClient telegramClient;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final MongoTemplate mongoTemplate;
    private final BotCommandPoller botCommandPoller;
    private long lastUpdateId;

    public BotCallbackHandler(TelegramClient telegramClient,
                               OrderService orderService,
                               OrderRepository orderRepository,
                               MongoTemplate mongoTemplate,
                               BotCommandPoller botCommandPoller) {
        this.telegramClient = telegramClient;
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.mongoTemplate = mongoTemplate;
        this.botCommandPoller = botCommandPoller;
        this.lastUpdateId = loadOffset();
    }

    @Scheduled(fixedDelay = 3000)
    public void pollUpdates() {
        List<JsonNode> updates = telegramClient.getUpdates(lastUpdateId + 1);

        for (JsonNode update : updates) {
            lastUpdateId = update.path("update_id").asLong();

            JsonNode callbackQuery = update.path("callback_query");
            if (!callbackQuery.isMissingNode()) {
                handleCallback(callbackQuery);
            }

            // Handle /create_admin bot command
            JsonNode message = update.path("message");
            if (!message.isMissingNode()) {
                String text = message.path("text").asText("");
                String chatId = message.path("chat").path("id").asText();
                if (text.startsWith("/create_admin")) {
                    String reply = botCommandPoller.handleCreateAdmin(chatId, text);
                    telegramClient.sendMessage(chatId, reply, null);
                }
            }
        }

        if (!updates.isEmpty()) {
            saveOffset(lastUpdateId);
        }
    }

    private long loadOffset() {
        try {
            Document doc = mongoTemplate.getCollection("bot_state")
                    .find(new org.bson.Document("_id", OFFSET_KEY)).first();
            return doc != null ? doc.getLong("value") : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private void saveOffset(long offset) {
        mongoTemplate.getCollection("bot_state").replaceOne(
                new org.bson.Document("_id", OFFSET_KEY),
                new org.bson.Document("_id", OFFSET_KEY).append("value", offset),
                new com.mongodb.client.model.ReplaceOptions().upsert(true));
    }

    private void handleCallback(JsonNode callbackQuery) {
        String callbackId = callbackQuery.path("id").asText();
        String data = callbackQuery.path("data").asText();
        String chatId = callbackQuery.path("message").path("chat").path("id").asText();
        int messageId = callbackQuery.path("message").path("message_id").asInt();

        try {
            String[] parts = data.split("_", 2);
            if (parts.length != 2) return;

            String action = parts[0];
            String orderId = parts[1];

            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                telegramClient.answerCallbackQuery(callbackId, "Order not found");
                return;
            }

            Order order = orderOpt.get();

            // Check if already cancelled (В2.6 — prevent action on cancelled orders)
            if (order.getStatus() == OrderStatus.CANCELLED) {
                telegramClient.answerCallbackQuery(callbackId, "Order already cancelled");
                telegramClient.editMessageReplyMarkup(chatId, messageId);
                return;
            }

            if ("confirm".equals(action)) {
                // If order awaits payment, mark as paid first, then confirm
                if (order.getStatus() == OrderStatus.WAITING_PAYMENT) {
                    orderService.updateStatus(orderId, OrderStatus.PENDING, null);
                }
                orderService.updateStatus(orderId, OrderStatus.CONFIRMED, null);
                telegramClient.answerCallbackQuery(callbackId, "Order confirmed");
            } else if ("cancel".equals(action)) {
                orderService.updateStatus(orderId, OrderStatus.CANCELLED, null);
                telegramClient.answerCallbackQuery(callbackId, "Order cancelled");
            }

            // Remove inline keyboard
            telegramClient.editMessageReplyMarkup(chatId, messageId);

        } catch (Exception e) {
            log.error("Failed to handle callback: {}", e.getMessage(), e);
            telegramClient.answerCallbackQuery(callbackId, "Error processing action");
        }
    }
}
