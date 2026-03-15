package com.africe.backend.telegram.worker;

import com.africe.backend.common.model.OutboxEvent;
import com.africe.backend.common.model.OutboxStatus;
import com.africe.backend.telegram.handler.TelegramNotificationHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxWorker {

    private static final int MAX_RETRIES = 5;

    private final TelegramOutboxEventRepository outboxEventRepository;
    private final TelegramNotificationHandler telegramNotificationHandler;
    private final MongoTemplate mongoTemplate;

    @Scheduled(fixedDelay = 5000)
    public void processOutboxEvents() {
        OutboxEvent event;
        while ((event = claimNextEvent()) != null) {
            processEvent(event);
        }
    }

    private OutboxEvent claimNextEvent() {
        Query query = Query.query(
            Criteria.where("status").is("PENDING")
                .and("retryCount").lt(MAX_RETRIES)
        );
        Update update = new Update()
            .set("status", "PROCESSING")
            .set("processedAt", Instant.now());
        return mongoTemplate.findAndModify(
            query, update,
            FindAndModifyOptions.options().returnNew(true),
            OutboxEvent.class
        );
    }

    private void processEvent(OutboxEvent event) {
        try {
            if ("ORDER_CREATED".equals(event.getType())) {
                telegramNotificationHandler.handleOrderCreated(event.getPayload());
            } else {
                log.warn("Unknown event type: {}", event.getType());
                return;
            }

            event.setStatus(OutboxStatus.SENT);
            event.setProcessedAt(Instant.now());
            outboxEventRepository.save(event);
            log.info("Successfully processed outbox event: {}", event.getId());
        } catch (Exception e) {
            event.setRetryCount(event.getRetryCount() + 1);
            if (event.getRetryCount() >= MAX_RETRIES) {
                event.setStatus(OutboxStatus.FAILED);
                log.error("Outbox event {} failed after {} retries", event.getId(), MAX_RETRIES, e);
            } else {
                event.setStatus(OutboxStatus.PENDING);
                log.warn("Outbox event {} failed, retry {}/{}", event.getId(), event.getRetryCount(), MAX_RETRIES, e);
            }
            outboxEventRepository.save(event);
        }
    }
}
