package com.africe.backend.notification.outbox;

import com.africe.backend.common.model.OutboxChannel;
import com.africe.backend.common.model.OutboxEvent;
import com.africe.backend.common.model.OutboxStatus;
import com.africe.backend.notification.email.EmailNotificationHandler;
import com.africe.backend.notification.telegram.TelegramNotificationHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
public class OutboxWorker {

    private static final int MAX_RETRIES = 5;

    private final MongoTemplate mongoTemplate;
    private final TelegramNotificationHandler telegramHandler;
    private final EmailNotificationHandler emailHandler;

    public OutboxWorker(MongoTemplate mongoTemplate,
                        TelegramNotificationHandler telegramHandler,
                        EmailNotificationHandler emailHandler) {
        this.mongoTemplate = mongoTemplate;
        this.telegramHandler = telegramHandler;
        this.emailHandler = emailHandler;
    }

    @Scheduled(fixedDelay = 5000)
    public void processOutbox() {
        OutboxEvent event;
        while ((event = claimNextEvent()) != null) {
            processEvent(event);
        }
    }

    private OutboxEvent claimNextEvent() {
        // Claim PENDING events, or reclaim PROCESSING events stuck for >2 minutes (crash recovery)
        Instant staleThreshold = Instant.now().minus(Duration.ofMinutes(2));
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("status").is(OutboxStatus.PENDING).and("retryCount").lt(MAX_RETRIES),
                Criteria.where("status").is(OutboxStatus.PROCESSING).and("processedAt").lt(staleThreshold)
        ));
        query.limit(1);

        Update update = new Update()
                .set("status", OutboxStatus.PROCESSING)
                .set("processedAt", Instant.now());

        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), OutboxEvent.class);
    }

    private void processEvent(OutboxEvent event) {
        try {
            if (event.getChannel() == OutboxChannel.TELEGRAM) {
                telegramHandler.handle(event.getType(), event.getPayload());
            } else if (event.getChannel() == OutboxChannel.EMAIL) {
                emailHandler.handle(event.getType(), event.getPayload());
            }

            // Mark as sent
            event.setStatus(OutboxStatus.SENT);
            event.setProcessedAt(Instant.now());
            mongoTemplate.save(event);

        } catch (Exception e) {
            log.error("Failed to process outbox event {}: {}", event.getId(), e.getMessage());
            // Return to PENDING for retry
            event.setStatus(OutboxStatus.PENDING);
            event.setRetryCount(event.getRetryCount() + 1);
            if (event.getRetryCount() >= MAX_RETRIES) {
                event.setStatus(OutboxStatus.FAILED);
            }
            mongoTemplate.save(event);
        }
    }
}
