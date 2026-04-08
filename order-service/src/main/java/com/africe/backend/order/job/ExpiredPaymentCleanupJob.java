package com.africe.backend.order.job;

import com.africe.backend.common.dto.ProductResponse;
import com.africe.backend.common.dto.ProductUpdateEvent;
import com.africe.backend.common.model.*;
import com.africe.backend.order.repository.OutboxEventRepository;
import com.africe.backend.order.service.OrderService;
import com.africe.backend.product.repository.ProductRepository;
import com.africe.backend.product.service.ProductEventService;
import com.africe.backend.product.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class ExpiredPaymentCleanupJob {

    private final MongoTemplate mongoTemplate;
    private final OutboxEventRepository outboxEventRepository;
    private final OrderService orderService;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final ProductEventService productEventService;
    private final CacheManager cacheManager;

    public ExpiredPaymentCleanupJob(MongoTemplate mongoTemplate,
                                    OutboxEventRepository outboxEventRepository,
                                    OrderService orderService,
                                    ProductRepository productRepository,
                                    ProductService productService,
                                    ProductEventService productEventService,
                                    CacheManager cacheManager) {
        this.mongoTemplate = mongoTemplate;
        this.outboxEventRepository = outboxEventRepository;
        this.orderService = orderService;
        this.productRepository = productRepository;
        this.productService = productService;
        this.productEventService = productEventService;
        this.cacheManager = cacheManager;
    }

    @Scheduled(fixedDelay = 300000) // every 5 minutes
    public void cancelExpiredOrders() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.MINUTES);

        // Atomic claim-and-cancel: prevents race with Monobank callback
        Order order;
        while ((order = claimExpiredOrder(cutoff)) != null) {
            try {
                orderService.restoreStock(order);
                broadcastStockChanges(order);

                outboxEventRepository.deleteByPayloadContainingAndStatus(
                        order.getId(), OutboxStatus.PENDING);

                log.info("Order #{} cancelled — payment not received within 30 min", order.getId());
            } catch (Exception e) {
                log.error("Failed to process expired order #{}", order.getId(), e);
            }
        }
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

    private Order claimExpiredOrder(Instant cutoff) {
        Query query = new Query(Criteria.where("status").is(OrderStatus.WAITING_PAYMENT)
                .and("createdAt").lt(cutoff));
        Update update = new Update().set("status", OrderStatus.CANCELLED);
        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), Order.class);
    }
}
