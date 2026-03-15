package com.africe.backend.order.service;

import com.africe.backend.common.dto.CheckoutItemRequest;
import com.africe.backend.common.dto.CheckoutRequest;
import com.africe.backend.common.dto.OrderItemResponse;
import com.africe.backend.common.dto.OrderResponse;
import com.africe.backend.common.dto.ShippingDetailsResponse;
import com.africe.backend.common.exception.InsufficientStockException;
import com.africe.backend.common.exception.ResourceNotFoundException;
import com.africe.backend.common.model.InventoryTransaction;
import com.africe.backend.common.model.Order;
import com.africe.backend.common.model.OrderItem;
import com.africe.backend.common.model.OrderStatus;
import com.africe.backend.common.model.OutboxEvent;
import com.africe.backend.common.model.OutboxStatus;
import com.africe.backend.common.model.Product;
import com.africe.backend.common.model.ProductVariant;
import com.africe.backend.common.model.ShippingDetails;
import com.africe.backend.order.repository.InventoryTransactionRepository;
import com.africe.backend.order.repository.OrderRepository;
import com.africe.backend.order.repository.OutboxEventRepository;
import com.africe.backend.product.service.ProductService;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProductService productService;
    private final MongoTemplate mongoTemplate;

    @Transactional
    public OrderResponse checkout(CheckoutRequest request) {
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CheckoutItemRequest itemRequest : request.getItems()) {
            Product product = productService.findById(itemRequest.getProductId());

            ProductVariant matchingVariant = product.getVariants().stream()
                    .filter(v -> v.getSku().equals(itemRequest.getSku()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", "sku", itemRequest.getSku()));

            // Atomic stock decrement
            Query query = new Query(Criteria.where("id").is(product.getId())
                    .and("variants.sku").is(itemRequest.getSku())
                    .and("variants.stock").gte(itemRequest.getQuantity()));
            Update update = new Update().inc("variants.$.stock", -itemRequest.getQuantity());
            UpdateResult updateResult = mongoTemplate.updateFirst(query, update, Product.class);

            if (updateResult.getModifiedCount() == 0) {
                throw new InsufficientStockException(itemRequest.getSku());
            }

            // Record inventory transaction
            InventoryTransaction inventoryTransaction = InventoryTransaction.builder()
                    .productId(product.getId())
                    .sku(itemRequest.getSku())
                    .change(-itemRequest.getQuantity())
                    .createdAt(Instant.now())
                    .build();
            inventoryTransactionRepository.save(inventoryTransaction);

            // Build order item snapshot
            BigDecimal unitPrice = product.getBasePrice().add(matchingVariant.getPriceModifier());
            OrderItem orderItem = OrderItem.builder()
                    .productId(product.getId())
                    .productTitle(product.getTitle())
                    .sku(itemRequest.getSku())
                    .variantName(matchingVariant.getAttributes() != null
                            ? String.join(", ", matchingVariant.getAttributes().values())
                            : "")
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(unitPrice)
                    .build();
            orderItems.add(orderItem);

            totalAmount = totalAmount.add(unitPrice.multiply(BigDecimal.valueOf(itemRequest.getQuantity())));
        }

        // Build and save order
        ShippingDetails shippingDetails = ShippingDetails.builder()
                .address(request.getShippingDetails().getAddress())
                .city(request.getShippingDetails().getCity())
                .postalCode(request.getShippingDetails().getPostalCode())
                .country(request.getShippingDetails().getCountry())
                .build();

        Order order = Order.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .items(orderItems)
                .totalAmount(totalAmount)
                .status(OrderStatus.PENDING)
                .shippingDetails(shippingDetails)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        order = orderRepository.save(order);

        // Update inventory transactions with orderId
        for (OrderItem item : orderItems) {
            // Already saved above; orderId was not yet known.
            // We set it now via a simple query update.
            Query txQuery = new Query(Criteria.where("productId").is(item.getProductId())
                    .and("sku").is(item.getSku())
                    .and("orderId").isNull());
            Update txUpdate = new Update().set("orderId", order.getId());
            mongoTemplate.updateFirst(txQuery, txUpdate, InventoryTransaction.class);
        }

        // Create outbox event
        String itemsSummary = orderItems.stream()
                .map(i -> String.format("%s (SKU: %s) x%d", i.getProductTitle(), i.getSku(), i.getQuantity()))
                .collect(Collectors.joining("\\n"));
        String customerName = order.getFirstName() + " " + order.getLastName();
        String payload = String.format(
                "{\"orderId\":\"%s\",\"items\":\"%s\",\"totalAmount\":\"%s\",\"customerName\":\"%s\"}",
                order.getId(), itemsSummary, order.getTotalAmount().toPlainString(), customerName);

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .type("ORDER_CREATED")
                .payload(payload)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();
        outboxEventRepository.save(outboxEvent);

        return toOrderResponse(order);
    }

    public Page<OrderResponse> listOrders(String search, OrderStatus status, Pageable pageable) {
        Page<Order> orders;
        if (status != null) {
            orders = orderRepository.findByStatus(status, pageable);
        } else if (search != null && !search.isBlank()) {
            orders = orderRepository.findByEmailContainingIgnoreCase(search, pageable);
        } else {
            orders = orderRepository.findAll(pageable);
        }
        return orders.map(this::toOrderResponse);
    }

    public OrderResponse updateStatus(String orderId, OrderStatus newStatus) {
        Order order = findById(orderId);
        order.setStatus(newStatus);
        order.setUpdatedAt(Instant.now());
        order = orderRepository.save(order);
        return toOrderResponse(order);
    }

    public Order findById(String id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
    }

    private OrderResponse toOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems() != null
                ? order.getItems().stream()
                    .map(item -> OrderItemResponse.builder()
                            .productId(item.getProductId())
                            .productTitle(item.getProductTitle())
                            .sku(item.getSku())
                            .variantName(item.getVariantName())
                            .quantity(item.getQuantity())
                            .unitPrice(item.getUnitPrice())
                            .build())
                    .collect(Collectors.toList())
                : Collections.emptyList();

        ShippingDetailsResponse shippingResponse = null;
        if (order.getShippingDetails() != null) {
            ShippingDetails sd = order.getShippingDetails();
            shippingResponse = ShippingDetailsResponse.builder()
                    .address(sd.getAddress())
                    .city(sd.getCity())
                    .postalCode(sd.getPostalCode())
                    .country(sd.getCountry())
                    .trackingNumber(sd.getTrackingNumber())
                    .carrier(sd.getCarrier())
                    .build();
        }

        return OrderResponse.builder()
                .id(order.getId())
                .firstName(order.getFirstName())
                .lastName(order.getLastName())
                .email(order.getEmail())
                .phone(order.getPhone())
                .items(itemResponses)
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus() != null ? order.getStatus().name() : null)
                .shippingDetails(shippingResponse)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
