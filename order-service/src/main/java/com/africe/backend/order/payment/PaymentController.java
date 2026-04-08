package com.africe.backend.order.payment;

import com.africe.backend.common.dto.CreatePaymentRequest;
import com.africe.backend.common.dto.ProductResponse;
import com.africe.backend.common.dto.ProductUpdateEvent;
import com.africe.backend.common.exception.ResourceNotFoundException;
import com.africe.backend.common.model.*;
import com.africe.backend.order.repository.OrderRepository;
import com.africe.backend.order.repository.OutboxEventRepository;
import com.africe.backend.order.service.OrderService;
import com.africe.backend.product.repository.ProductRepository;
import com.africe.backend.product.service.ProductEventService;
import com.africe.backend.product.service.ProductService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final MonobankClient monobankClient;
    private final MonobankWebhookValidator webhookValidator;
    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final MongoTemplate mongoTemplate;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final ProductEventService productEventService;
    private final CacheManager cacheManager;

    public PaymentController(MonobankClient monobankClient,
                             MonobankWebhookValidator webhookValidator,
                             OrderRepository orderRepository,
                             OutboxEventRepository outboxEventRepository,
                             OrderService orderService,
                             ObjectMapper objectMapper,
                             MongoTemplate mongoTemplate,
                             ProductRepository productRepository,
                             ProductService productService,
                             ProductEventService productEventService,
                             CacheManager cacheManager) {
        this.monobankClient = monobankClient;
        this.webhookValidator = webhookValidator;
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.orderService = orderService;
        this.objectMapper = objectMapper;
        this.mongoTemplate = mongoTemplate;
        this.productRepository = productRepository;
        this.productService = productService;
        this.productEventService = productEventService;
        this.cacheManager = cacheManager;
    }

    @PostMapping("/create")
    @RateLimiter(name = "payment")
    public Map<String, String> createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        String orderId = request.orderId();
        String accessToken = request.accessToken();
        String redirectUrl = request.redirectUrl() != null ? request.redirectUrl() : "/";

        Order order = orderRepository.findByIdAndAccessToken(orderId, accessToken)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (order.getStatus() != OrderStatus.WAITING_PAYMENT) {
            throw new IllegalArgumentException("Order is not awaiting payment");
        }

        MonobankClient.InvoiceResponse invoice = monobankClient.createInvoice(
                order.getTotalAmount(), orderId, redirectUrl);

        return Map.of("paymentUrl", invoice.pageUrl());
    }

    @PostMapping("/callback")
    public ResponseEntity<Void> handleCallback(
            @RequestHeader(value = "X-Sign", required = false) String xSign,
            @RequestBody String body) {

        // Validate webhook signature
        if (xSign == null || !webhookValidator.validateSignature(body, xSign)) {
            log.warn("Invalid Monobank webhook signature");
            return ResponseEntity.badRequest().build();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            String status = root.path("status").asText();
            String orderId = root.path("reference").asText();

            if (orderId.isBlank()) {
                log.warn("Monobank callback missing reference");
                return ResponseEntity.badRequest().build();
            }

            if ("success".equals(status)) {
                // Atomic: only transition WAITING_PAYMENT → PENDING (prevents race with cleanup job)
                Order claimed = atomicStatusTransition(orderId, OrderStatus.WAITING_PAYMENT, OrderStatus.PENDING);
                if (claimed != null) {
                    // Create Telegram outbox event directly (not via updateStatus to avoid double-write)
                    try {
                        String payload = objectMapper.writeValueAsString(orderService.toResponse(claimed));
                        outboxEventRepository.save(OutboxEvent.builder()
                                .channel(OutboxChannel.TELEGRAM)
                                .type("ORDER_CREATED")
                                .payload(payload)
                                .build());
                    } catch (Exception outboxErr) {
                        log.error("Failed to create outbox event for paid order #{}", orderId, outboxErr);
                    }
                    log.info("Payment successful for order #{}", orderId);
                }
            } else if ("failure".equals(status) || "expired".equals(status)) {
                Order claimed = atomicStatusTransition(orderId, OrderStatus.WAITING_PAYMENT, OrderStatus.CANCELLED);
                if (claimed != null) {
                    orderService.restoreStock(claimed);
                    broadcastStockChanges(claimed);
                    log.info("Payment failed/expired for order #{}, stock restored", orderId);
                }
            }

        } catch (Exception e) {
            log.error("Failed to process Monobank callback: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }

    private void broadcastStockChanges(Order order) {
        var products = cacheManager.getCache("products");
        if (products != null) products.clear();
        var productBySlug = cacheManager.getCache("productBySlug");
        if (productBySlug != null) productBySlug.clear();
        var productFilters = cacheManager.getCache("productFilters");
        if (productFilters != null) productFilters.clear();

        Set<String> notified = new HashSet<>();
        for (OrderItem item : order.getItems()) {
            if (notified.add(item.getProductId())) {
                try {
                    Product product = productRepository.findById(item.getProductId()).orElse(null);
                    if (product != null) {
                        ProductResponse resp = productService.toResponse(product);
                        productEventService.broadcast(new ProductUpdateEvent(
                                ProductUpdateEvent.STOCK_CHANGED,
                                product.getId(), product.getSlug(),
                                resp.minPrice(), resp.variants(), resp.attributes(),
                                product.getStatus()));
                    }
                } catch (Exception e) {
                    log.warn("Failed to broadcast stock change for product {}", item.getProductId(), e);
                }
            }
        }
    }

    /**
     * Atomically transitions order from expectedStatus to newStatus.
     * Returns the order (with new status) if transition succeeded, null if already changed.
     */
    private Order atomicStatusTransition(String orderId, OrderStatus expectedStatus, OrderStatus newStatus) {
        Query query = new Query(Criteria.where("_id").is(orderId).and("status").is(expectedStatus));
        Update update = new Update().set("status", newStatus);
        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), Order.class);
    }
}
