package com.africe.backend.telegram.worker;

import com.africe.backend.common.model.OutboxEvent;
import com.africe.backend.common.model.OutboxStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TelegramOutboxEventRepository extends MongoRepository<OutboxEvent, String> {

    List<OutboxEvent> findByStatusAndRetryCountLessThan(OutboxStatus status, int maxRetries);
}
