package com.africe.backend.order.payment;

import com.africe.backend.common.model.Order;
import com.africe.backend.common.model.OrderStatus;
import com.africe.backend.common.model.OutboxEvent;
import com.africe.backend.order.repository.OrderRepository;
import com.africe.backend.order.repository.OutboxEventRepository;
import com.africe.backend.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock MonobankClient monobankClient;
    @Mock MonobankWebhookValidator webhookValidator;
    @Mock OrderRepository orderRepository;
    @Mock OutboxEventRepository outboxEventRepository;
    @Mock OrderService orderService;
    @Mock ObjectMapper objectMapper;
    @Mock MongoTemplate mongoTemplate;
    @InjectMocks PaymentController paymentController;

    @Test
    void createPayment_success() {
        Order order = Order.builder()
                .id("o1").status(OrderStatus.WAITING_PAYMENT)
                .totalAmount(BigDecimal.valueOf(1000)).accessToken("tok").build();
        when(orderRepository.findByIdAndAccessToken("o1", "tok")).thenReturn(Optional.of(order));
        when(monobankClient.createInvoice(BigDecimal.valueOf(1000), "o1", "/success"))
                .thenReturn(new MonobankClient.InvoiceResponse("inv1", "https://pay.mono/inv1"));

        Map<String, String> result = paymentController.createPayment(
                Map.of("orderId", "o1", "accessToken", "tok", "redirectUrl", "/success"));

        assertThat(result.get("paymentUrl")).isEqualTo("https://pay.mono/inv1");
    }

    @Test
    void createPayment_orderNotWaiting_throws() {
        Order order = Order.builder()
                .id("o1").status(OrderStatus.PENDING).accessToken("tok").build();
        when(orderRepository.findByIdAndAccessToken("o1", "tok")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentController.createPayment(
                Map.of("orderId", "o1", "accessToken", "tok")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not awaiting payment");
    }

    @Test
    void callback_invalidSignature_returnsBadRequest() {
        when(webhookValidator.validateSignature("body", "bad-sig")).thenReturn(false);

        ResponseEntity<Void> response = paymentController.handleCallback("bad-sig", "body");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void callback_noSignature_returnsBadRequest() {
        ResponseEntity<Void> response = paymentController.handleCallback(null, "body");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void callback_successPayment_createsOutboxEvent() throws Exception {
        String body = "{\"status\":\"success\",\"reference\":\"o1\"}";
        when(webhookValidator.validateSignature(body, "valid-sig")).thenReturn(true);
        when(objectMapper.readTree(body)).thenReturn(
                new ObjectMapper().readTree(body));

        // Atomic transition returns the claimed order
        Order claimed = Order.builder()
                .id("o1").firstName("J").lastName("D").email("j@t.com").phone("+380")
                .status(OrderStatus.PENDING).items(List.of()).build();
        when(mongoTemplate.findAndModify(any(), any(), any(), eq(Order.class)))
                .thenReturn(claimed);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        ResponseEntity<Void> response = paymentController.handleCallback("valid-sig", body);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        // Outbox event created directly, not via updateStatus
        verify(outboxEventRepository).save(any(OutboxEvent.class));
        verify(orderService, never()).updateStatus(any(), any(), any());
    }

    @Test
    void callback_failedPayment_cancelsAndRestoresStock() throws Exception {
        String body = "{\"status\":\"failure\",\"reference\":\"o1\"}";
        when(webhookValidator.validateSignature(body, "valid-sig")).thenReturn(true);
        when(objectMapper.readTree(body)).thenReturn(
                new ObjectMapper().readTree(body));

        Order claimed = Order.builder()
                .id("o1").status(OrderStatus.CANCELLED).items(List.of()).build();
        when(mongoTemplate.findAndModify(any(), any(), any(), eq(Order.class)))
                .thenReturn(claimed);

        ResponseEntity<Void> response = paymentController.handleCallback("valid-sig", body);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(orderService).restoreStock(claimed);
    }
}
