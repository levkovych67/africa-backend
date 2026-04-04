package com.africe.backend.notification.telegram;

import com.africe.backend.common.dto.OrderResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TelegramNotificationHandler {

    private final TelegramClient telegramClient;
    private final ObjectMapper objectMapper;
    private final List<String> chatIds;
    private final String frontendUrl;

    public TelegramNotificationHandler(TelegramClient telegramClient,
                                        ObjectMapper objectMapper,
                                        @Value("${telegram.chat-ids}") List<String> chatIds,
                                        @Value("${frontend.url:http://localhost:3000}") String frontendUrl) {
        this.telegramClient = telegramClient;
        this.objectMapper = objectMapper;
        this.chatIds = chatIds;
        this.frontendUrl = frontendUrl;
    }

    public void handle(String type, String payload) {
        try {
            OrderResponse order = objectMapper.readValue(payload, OrderResponse.class);

            switch (type) {
                case "ORDER_CREATED" -> handleOrderCreated(order);
                case "ORDER_CONFIRMED" -> handleOrderConfirmed(order);
                case "ORDER_SHIPPED" -> handleOrderShipped(order);
                default -> log.debug("Unhandled Telegram event type: {}", type);
            }
        } catch (Exception e) {
            log.error("Failed to handle Telegram notification: {}", e.getMessage(), e);
            throw new RuntimeException("Telegram notification failed", e);
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void handleOrderCreated(OrderResponse order) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>New order #").append(esc(order.id())).append("</b>\n\n");
        sb.append(esc(order.firstName())).append(" ").append(esc(order.lastName())).append("\n");
        sb.append(esc(order.email())).append(" | ").append(esc(order.phone())).append("\n\n");

        if (order.items() != null) {
            for (var item : order.items()) {
                String productLink = item.productSlug() != null
                        ? "<a href=\"" + esc(frontendUrl) + "/product/" + esc(item.productSlug()) + "\">" + esc(item.productTitle()) + "</a>"
                        : esc(item.productTitle());
                sb.append("- ").append(productLink)
                        .append(" (").append(esc(item.variantName())).append(")")
                        .append(" x").append(item.quantity())
                        .append(" = ").append(item.unitPrice().multiply(java.math.BigDecimal.valueOf(item.quantity())))
                        .append(" UAH\n");
            }
        }

        sb.append("\n<b>Total: ").append(order.totalAmount()).append(" UAH</b>\n");
        sb.append("Payment: ").append(order.paymentMethod()).append("\n");

        if (order.shippingDetails() != null) {
            sb.append("Shipping: ").append(esc(order.shippingDetails().city()))
                    .append(", ").append(esc(order.shippingDetails().warehouseDescription())).append("\n");
        }

        if (order.comment() != null && !order.comment().isBlank()) {
            sb.append("Comment: ").append(esc(order.comment())).append("\n");
        }

        List<List<Map<String, String>>> keyboard = List.of(
                List.of(
                        Map.of("text", "Confirm", "callback_data", "confirm_" + order.id()),
                        Map.of("text", "Cancel", "callback_data", "cancel_" + order.id())
                )
        );

        for (String chatId : chatIds) {
            telegramClient.sendMessage(chatId, sb.toString(), keyboard);
        }
    }

    private void handleOrderConfirmed(OrderResponse order) {
        String text = String.format(
                "<b>Order #%s confirmed</b>\n\n%s %s — order is being processed.",
                esc(order.id()), esc(order.firstName()), esc(order.lastName()));

        for (String chatId : chatIds) {
            telegramClient.sendMessage(chatId, text, null);
        }
    }

    private void handleOrderShipped(OrderResponse order) {
        String trackingNumber = order.shippingDetails() != null
                ? order.shippingDetails().trackingNumber()
                : "N/A";

        String text = String.format(
                "<b>Order #%s shipped</b>\n\nTracking: %s\nCarrier: Nova Poshta",
                esc(order.id()), esc(trackingNumber));

        // This would be sent to customer's Telegram if they have one
        // For now, notify admin chat
        for (String chatId : chatIds) {
            telegramClient.sendMessage(chatId, text, null);
        }
    }
}
