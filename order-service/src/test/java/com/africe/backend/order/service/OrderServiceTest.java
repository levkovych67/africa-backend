package com.africe.backend.order.service;

import com.africe.backend.common.dto.*;
import com.africe.backend.common.exception.InsufficientStockException;
import com.africe.backend.common.exception.ResourceNotFoundException;
import com.africe.backend.common.model.*;
import com.africe.backend.order.repository.OrderRepository;
import com.africe.backend.order.repository.OutboxEventRepository;
import com.africe.backend.product.repository.ProductRepository;
import com.africe.backend.product.service.ProductEventService;
import com.africe.backend.product.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.CacheManager;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock OutboxEventRepository outboxEventRepository;
    @Mock ProductRepository productRepository;
    @Mock MongoTemplate mongoTemplate;
    @Mock ObjectMapper objectMapper;
    @Mock ProductEventService productEventService;
    @Mock ProductService productService;
    @Mock CacheManager cacheManager;
    @InjectMocks OrderService orderService;

    private Product createProduct() {
        return Product.builder()
                .id("prod1").title("T-Shirt")
                .status(ProductStatus.ACTIVE)
                .variants(List.of(ProductVariant.builder()
                        .sku("SKU-M").attributes(Map.of("Size", "M"))
                        .price(BigDecimal.valueOf(550)).stock(10)
                        .build()))
                .minPrice(BigDecimal.valueOf(550))
                .build();
    }

    @Test
    void checkout_COD_createsPendingOrder() throws Exception {
        Product product = createProduct();
        when(productRepository.findById("prod1")).thenReturn(Optional.of(product));

        UpdateResult updateResult = mock(UpdateResult.class);
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Product.class)))
                .thenReturn(updateResult);

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId("order1");
            return o;
        });
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        CheckoutRequest request = new CheckoutRequest(
                "John", "Doe", "john@test.com", "+380991234567",
                List.of(new CheckoutItemRequest("prod1", "SKU-M", 2)),
                new ShippingDetailsRequest("Kyiv", "ref1", "wh1", "Warehouse #1"),
                "Test comment", PaymentMethod.COD);

        OrderResponse response = orderService.checkout(request);

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.accessToken()).isNotBlank();
        // Price: 550 * 2 = 1100
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1100));

        // Verify outbox event created for Telegram
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void checkout_ONLINE_createsWaitingPaymentOrder() throws Exception {
        Product product = createProduct();
        when(productRepository.findById("prod1")).thenReturn(Optional.of(product));

        UpdateResult updateResult = mock(UpdateResult.class);
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Product.class)))
                .thenReturn(updateResult);

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId("order2");
            return o;
        });

        CheckoutRequest request = new CheckoutRequest(
                "Jane", "Doe", "jane@test.com", "+380991234567",
                List.of(new CheckoutItemRequest("prod1", "SKU-M", 1)),
                new ShippingDetailsRequest("Lviv", "ref2", "wh2", "Warehouse #5"),
                null, PaymentMethod.ONLINE);

        OrderResponse response = orderService.checkout(request);

        assertThat(response.status()).isEqualTo(OrderStatus.WAITING_PAYMENT);
    }

    @Test
    void checkout_insufficientStock_throws() {
        Product product = createProduct();
        when(productRepository.findById("prod1")).thenReturn(Optional.of(product));

        UpdateResult updateResult = mock(UpdateResult.class);
        when(updateResult.getModifiedCount()).thenReturn(0L);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Product.class)))
                .thenReturn(updateResult);

        CheckoutRequest request = new CheckoutRequest(
                "John", "Doe", "john@test.com", "+380991234567",
                List.of(new CheckoutItemRequest("prod1", "SKU-M", 999)),
                new ShippingDetailsRequest("Kyiv", "ref1", "wh1", "Warehouse #1"),
                null, PaymentMethod.COD);

        assertThatThrownBy(() -> orderService.checkout(request))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void checkout_productNotFound_throws() {
        when(productRepository.findById("nonexistent")).thenReturn(Optional.empty());

        CheckoutRequest request = new CheckoutRequest(
                "John", "Doe", "john@test.com", "+380991234567",
                List.of(new CheckoutItemRequest("nonexistent", "SKU", 1)),
                new ShippingDetailsRequest("Kyiv", "ref1", "wh1", "Warehouse"),
                null, PaymentMethod.COD);

        assertThatThrownBy(() -> orderService.checkout(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void checkout_variantNotFound_throws() {
        Product product = createProduct();
        when(productRepository.findById("prod1")).thenReturn(Optional.of(product));

        CheckoutRequest request = new CheckoutRequest(
                "John", "Doe", "john@test.com", "+380991234567",
                List.of(new CheckoutItemRequest("prod1", "WRONG-SKU", 1)),
                new ShippingDetailsRequest("Kyiv", "ref1", "wh1", "Warehouse"),
                null, PaymentMethod.COD);

        assertThatThrownBy(() -> orderService.checkout(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getOrder_found() {
        Order order = Order.builder().id("o1").firstName("John").lastName("Doe")
                .email("j@t.com").phone("+380").status(OrderStatus.PENDING)
                .items(List.of()).build();
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrder("o1");

        assertThat(response.id()).isEqualTo("o1");
    }

    @Test
    void getOrder_notFound_throws() {
        when(orderRepository.findById("o1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder("o1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatus_shipped_requiresTrackingNumber() {
        Order order = Order.builder().id("o1").status(OrderStatus.CONFIRMED)
                .items(List.of()).build();
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus("o1", OrderStatus.SHIPPED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tracking number");
    }

    @Test
    void updateStatus_shipped_setsTrackingNumber() throws Exception {
        Order order = Order.builder().id("o1").status(OrderStatus.CONFIRMED)
                .shippingDetails(ShippingDetails.builder().city("Kyiv").build())
                .items(List.of()).build();
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        OrderResponse response = orderService.updateStatus("o1", OrderStatus.SHIPPED, "20450012345678");

        assertThat(response.status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(response.shippingDetails().trackingNumber()).isEqualTo("20450012345678");
    }

    @Test
    void updateStatus_cancelled_restoresStock() throws Exception {
        OrderItem item = OrderItem.builder()
                .productId("prod1").sku("SKU-M").quantity(3).build();
        Order order = Order.builder().id("o1").status(OrderStatus.PENDING)
                .items(List.of(item)).build();
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        orderService.updateStatus("o1", OrderStatus.CANCELLED, null);

        // Verify stock restored via MongoTemplate
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(Product.class));
    }

    @Test
    void checkout_serverSidePriceCalculation() throws Exception {
        // Verify price is calculated server-side, not from client
        Product product = Product.builder()
                .id("p1").title("Hoodie")
                .status(ProductStatus.ACTIVE)
                .variants(List.of(ProductVariant.builder()
                        .sku("H-XL").attributes(Map.of("Size", "XL"))
                        .price(BigDecimal.valueOf(1200)).stock(5)
                        .build()))
                .minPrice(BigDecimal.valueOf(1200))
                .build();
        when(productRepository.findById("p1")).thenReturn(Optional.of(product));

        UpdateResult updateResult = mock(UpdateResult.class);
        when(updateResult.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Product.class)))
                .thenReturn(updateResult);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId("o1");
            return o;
        });
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        CheckoutRequest request = new CheckoutRequest(
                "A", "B", "a@b.com", "+380",
                List.of(new CheckoutItemRequest("p1", "H-XL", 3)),
                new ShippingDetailsRequest("Kyiv", "r", "w", "W"),
                null, PaymentMethod.COD);

        OrderResponse response = orderService.checkout(request);

        // 1200 * 3 = 3600
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(3600));
        assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo(BigDecimal.valueOf(1200));
    }
}
