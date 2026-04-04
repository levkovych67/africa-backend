package com.africe.backend.notification.telegram;

import com.africe.backend.common.dto.*;
import com.africe.backend.common.model.OrderStatus;
import com.africe.backend.common.model.PaymentMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TelegramNotificationHandlerTest {

    @Mock TelegramClient telegramClient;
    private TelegramNotificationHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        handler = new TelegramNotificationHandler(telegramClient, objectMapper, List.of("12345"));
    }

    @Test
    void handleOrderCreated_sendsMessageWithButtons() throws Exception {
        OrderResponse order = new OrderResponse(
                "o1", "John", "Doe", "j@t.com", "+380",
                List.of(new OrderItemResponse("p1", "T-Shirt", "SKU-M", "M", 2, BigDecimal.valueOf(500))),
                BigDecimal.valueOf(1000), OrderStatus.PENDING, PaymentMethod.COD,
                new ShippingDetailsResponse("Kyiv", "r1", "w1", "Warehouse #1", null, "Nova Poshta"),
                null, "token", null, null);

        String payload = objectMapper.writeValueAsString(order);
        handler.handle("ORDER_CREATED", payload);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(eq("12345"), textCaptor.capture(), anyList());

        String text = textCaptor.getValue();
        assertThat(text).contains("New order #o1");
        assertThat(text).contains("John Doe");
        assertThat(text).contains("T-Shirt");
        assertThat(text).contains("1000");
    }

    @Test
    void handleOrderShipped_sendsTrackingNumber() throws Exception {
        OrderResponse order = new OrderResponse(
                "o1", "John", "Doe", "j@t.com", "+380", List.of(),
                BigDecimal.valueOf(500), OrderStatus.SHIPPED, PaymentMethod.COD,
                new ShippingDetailsResponse("Kyiv", "r1", "w1", "W1", "20450012345", "Nova Poshta"),
                null, "token", null, null);

        String payload = objectMapper.writeValueAsString(order);
        handler.handle("ORDER_SHIPPED", payload);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(eq("12345"), textCaptor.capture(), isNull());

        assertThat(textCaptor.getValue()).contains("20450012345");
    }

    @Test
    void handleOrderConfirmed_sendsConfirmation() throws Exception {
        OrderResponse order = new OrderResponse(
                "o1", "John", "Doe", "j@t.com", "+380", List.of(),
                BigDecimal.valueOf(500), OrderStatus.CONFIRMED, PaymentMethod.COD,
                null, null, "token", null, null);

        String payload = objectMapper.writeValueAsString(order);
        handler.handle("ORDER_CONFIRMED", payload);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(eq("12345"), textCaptor.capture(), isNull());

        assertThat(textCaptor.getValue()).contains("confirmed");
    }
}
