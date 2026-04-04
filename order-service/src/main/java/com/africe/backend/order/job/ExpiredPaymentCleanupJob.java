package com.africe.backend.order.job;

import com.africe.backend.common.model.Order;
import com.africe.backend.common.model.OrderStatus;
import com.africe.backend.common.model.OutboxStatus;
import com.africe.backend.order.repository.OutboxEventRepository;
import com.africe.backend.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
public class ExpiredPaymentCleanupJob {

    private final MongoTemplate mongoTemplate;
    private final OutboxEventRepository outboxEventRepository;
    private final OrderService orderService;

    public ExpiredPaymentCleanupJob(MongoTemplate mongoTemplate,
                                    OutboxEventRepository outboxEventRepository,
                                    OrderService orderService) {
        this.mongoTemplate = mongoTemplate;
        this.outboxEventRepository = outboxEventRepository;
        this.orderService = orderService;
    }

    @Scheduled(fixedDelay = 300000) // every 5 minutes
    public void cancelExpiredOrders() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.MINUTES);

        // Atomic claim-and-cancel: prevents race with Monobank callback
        Order order;
        while ((order = claimExpiredOrder(cutoff)) != null) {
            try {
                orderService.restoreStock(order);

                outboxEventRepository.deleteByPayloadContainingAndStatus(
                        order.getId(), OutboxStatus.PENDING);

                log.info("Order #{} cancelled — payment not received within 30 min", order.getId());
            } catch (Exception e) {
                log.error("Failed to process expired order #{}", order.getId(), e);
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
