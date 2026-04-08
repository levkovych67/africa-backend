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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

@Slf4j
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProductRepository productRepository;
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final ProductEventService productEventService;
    private final ProductService productService;

    public OrderService(OrderRepository orderRepository,
                        OutboxEventRepository outboxEventRepository,
                        ProductRepository productRepository,
                        MongoTemplate mongoTemplate,
                        ObjectMapper objectMapper,
                        ProductEventService productEventService,
                        ProductService productService) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.productRepository = productRepository;
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = objectMapper;
        this.productEventService = productEventService;
        this.productService = productService;
    }

    public OrderResponse checkout(CheckoutRequest request) {
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        // Track decremented stock so we can rollback on partial failure
        List<OrderItem> decrementedItems = new ArrayList<>();

        try {
            for (CheckoutItemRequest item : request.items()) {
                Product product = productRepository.findById(item.productId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product", "id", item.productId()));

                // Only allow checkout of ACTIVE products
                if (product.getStatus() != ProductStatus.ACTIVE) {
                    throw new IllegalArgumentException("Product is not available: " + product.getTitle());
                }

                if (product.getVariants() == null || product.getVariants().isEmpty()) {
                    throw new IllegalArgumentException(
                            "Product has no variants and cannot be purchased: " + product.getTitle());
                }

                ProductVariant variant = product.getVariants().stream()
                        .filter(v -> v.getSku().equals(item.sku()))
                        .findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException("Variant", "sku", item.sku()));

                // Atomic stock decrement — prevents negative stock at DB level
                Query query = new Query(Criteria.where("id").is(product.getId())
                        .and("variants.sku").is(item.sku())
                        .and("variants.stock").gte(item.quantity()));
                Update update = new Update().inc("variants.$.stock", -item.quantity());
                var result = mongoTemplate.updateFirst(query, update, Product.class);

                if (result.getModifiedCount() == 0) {
                    String variantName = variant.getAttributes() != null
                            ? String.join(" / ", variant.getAttributes().values())
                            : item.sku();
                    throw new InsufficientStockException(
                            "Товар «%s» (%s) — недостатньо на складі".formatted(
                                    product.getTitle(), variantName));
                }

                // Track for potential rollback
                decrementedItems.add(OrderItem.builder()
                        .productId(product.getId()).sku(item.sku()).quantity(item.quantity()).build());

                // Server-side price calculation — never trust client price
                BigDecimal unitPrice = variant.getPrice();
                BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.quantity()));
                totalAmount = totalAmount.add(lineTotal);

                // Build variant name from attributes
                String variantName = variant.getAttributes() != null
                        ? String.join(" / ", variant.getAttributes().values())
                        : variant.getSku();

                orderItems.add(OrderItem.builder()
                        .productId(product.getId())
                        .productTitle(product.getTitle())
                        .productSlug(product.getSlug())
                        .sku(item.sku())
                        .variantName(variantName)
                        .quantity(item.quantity())
                        .unitPrice(unitPrice)
                        .build());
            }
            // Status depends on payment method
            OrderStatus status = request.paymentMethod() == PaymentMethod.ONLINE
                    ? OrderStatus.WAITING_PAYMENT
                    : OrderStatus.PENDING;

            ShippingDetailsRequest sr = request.shippingDetails();
            ShippingDetails shippingDetails = ShippingDetails.builder()
                    .city(sr.city())
                    .cityRef(sr.cityRef())
                    .warehouseRef(sr.warehouseRef())
                    .warehouseDescription(sr.warehouseDescription())
                    .carrier("Nova Poshta")
                    .build();

            Order order = Order.builder()
                    .firstName(request.firstName())
                    .lastName(request.lastName())
                    .email(request.email())
                    .phone(request.phone())
                    .items(orderItems)
                    .totalAmount(totalAmount)
                    .status(status)
                    .paymentMethod(request.paymentMethod())
                    .shippingDetails(shippingDetails)
                    .comment(request.comment())
                    .accessToken(UUID.randomUUID().toString())
                    .build();

            order = orderRepository.save(order);

            // Telegram notification only for COD orders (paid online orders get notified via payment callback)
            if (status == OrderStatus.PENDING) {
                createOutboxEvent(OutboxChannel.TELEGRAM, "ORDER_CREATED", order);
            }

            // Broadcast stock changes to all connected clients via SSE
            broadcastStockChanges(order.getItems());

            return toResponse(order);

        } catch (Exception e) {
            // Rollback already-decremented stock on any failure (including order save failure)
            for (OrderItem dec : decrementedItems) {
                try {
                    Query q = new Query(Criteria.where("id").is(dec.getProductId())
                            .and("variants.sku").is(dec.getSku()));
                    Update u = new Update().inc("variants.$.stock", dec.getQuantity());
                    mongoTemplate.updateFirst(q, u, Product.class);
                } catch (Exception rollbackEx) {
                    log.error("Failed to rollback stock for {} sku {}", dec.getProductId(), dec.getSku(), rollbackEx);
                }
            }
            throw e;
        }
    }

    public OrderResponse getOrder(String id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
        return toResponse(order);
    }

    public OrderResponse updateStatus(String id, OrderStatus newStatus, String trackingNumber) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        if (newStatus == OrderStatus.SHIPPED && (trackingNumber == null || trackingNumber.isBlank())) {
            throw new IllegalArgumentException("Tracking number is required for SHIPPED status");
        }

        // WAITING_PAYMENT orders can only move to PENDING (mark paid) or CANCELLED
        if (order.getStatus() == OrderStatus.WAITING_PAYMENT
                && newStatus != OrderStatus.PENDING
                && newStatus != OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Спочатку підтвердіть оплату або скасуйте замовлення");
        }

        if (newStatus == OrderStatus.CANCELLED && order.getStatus() != OrderStatus.CANCELLED) {
            restoreStock(order);
            broadcastStockChanges(order.getItems());
        }

        order.setStatus(newStatus);

        if (trackingNumber != null && !trackingNumber.isBlank()) {
            ShippingDetails sd = order.getShippingDetails();
            if (sd == null) {
                sd = new ShippingDetails();
                order.setShippingDetails(sd);
            }
            sd.setTrackingNumber(trackingNumber);
        }

        order = orderRepository.save(order);

        // Create outbox events based on status change
        String eventType = "ORDER_" + newStatus.name();
        if (newStatus == OrderStatus.CONFIRMED || newStatus == OrderStatus.SHIPPED
                || newStatus == OrderStatus.DELIVERED || newStatus == OrderStatus.CANCELLED) {
            createOutboxEvent(OutboxChannel.EMAIL, eventType, order);
        }
        if (newStatus == OrderStatus.CONFIRMED || newStatus == OrderStatus.SHIPPED) {
            createOutboxEvent(OutboxChannel.TELEGRAM, eventType, order);
        }

        return toResponse(order);
    }

    public void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            Query query = new Query(Criteria.where("id").is(item.getProductId())
                    .and("variants.sku").is(item.getSku()));
            Update update = new Update().inc("variants.$.stock", item.getQuantity());
            mongoTemplate.updateFirst(query, update, Product.class);
        }
    }

    private void createOutboxEvent(OutboxChannel channel, String type, Order order) {
        try {
            // Strip accessToken from outbox payloads — it should only be in checkout response
            OrderResponse resp = toResponse(order);
            OrderResponse safeResp = new OrderResponse(
                    resp.id(), resp.firstName(), resp.lastName(), resp.email(), resp.phone(),
                    resp.items(), resp.totalAmount(), resp.status(), resp.paymentMethod(),
                    resp.shippingDetails(), resp.comment(), null, resp.createdAt(), resp.updatedAt());
            String payload = objectMapper.writeValueAsString(safeResp);
            OutboxEvent event = OutboxEvent.builder()
                    .channel(channel)
                    .type(type)
                    .payload(payload)
                    .build();
            outboxEventRepository.save(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize order for outbox event", e);
        }
    }

    private void broadcastStockChanges(List<OrderItem> items) {
        Set<String> notified = new HashSet<>();
        for (OrderItem item : items) {
            if (notified.add(item.getProductId())) {
                try {
                    Product product = productRepository.findById(item.getProductId()).orElse(null);
                    if (product != null) {
                        ProductResponse resp = productService.toResponse(product);
                        productEventService.broadcast(new ProductUpdateEvent(
                                ProductUpdateEvent.STOCK_CHANGED,
                                product.getId(),
                                product.getSlug(),
                                resp.minPrice(),
                                resp.variants(),
                                resp.attributes(),
                                product.getStatus()));
                    }
                } catch (Exception e) {
                    log.warn("Failed to broadcast stock change for product {}", item.getProductId(), e);
                }
            }
        }
    }

    public OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems() != null
                ? order.getItems().stream()
                .map(i -> new OrderItemResponse(
                        i.getProductId(), i.getProductTitle(), i.getProductSlug(),
                        i.getSku(), i.getVariantName(), i.getQuantity(), i.getUnitPrice()))
                .toList()
                : List.of();

        ShippingDetailsResponse shipping = null;
        if (order.getShippingDetails() != null) {
            ShippingDetails sd = order.getShippingDetails();
            shipping = new ShippingDetailsResponse(
                    sd.getCity(), sd.getCityRef(), sd.getWarehouseRef(),
                    sd.getWarehouseDescription(), sd.getTrackingNumber(), sd.getCarrier());
        }

        return new OrderResponse(
                order.getId(), order.getFirstName(), order.getLastName(),
                order.getEmail(), order.getPhone(), items, order.getTotalAmount(),
                order.getStatus(), order.getPaymentMethod(), shipping,
                order.getComment(), order.getAccessToken(),
                order.getCreatedAt(), order.getUpdatedAt());
    }
}
