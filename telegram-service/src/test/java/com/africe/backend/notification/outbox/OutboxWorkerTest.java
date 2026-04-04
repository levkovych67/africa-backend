package com.africe.backend.notification.outbox;

import com.africe.backend.common.model.OutboxChannel;
import com.africe.backend.common.model.OutboxEvent;
import com.africe.backend.common.model.OutboxStatus;
import com.africe.backend.notification.email.EmailNotificationHandler;
import com.africe.backend.notification.telegram.TelegramNotificationHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxWorkerTest {

    @Mock MongoTemplate mongoTemplate;
    @Mock TelegramNotificationHandler telegramHandler;
    @Mock EmailNotificationHandler emailHandler;
    @InjectMocks OutboxWorker outboxWorker;

    @Test
    void processOutbox_telegramEvent_delegatesToTelegramHandler() {
        OutboxEvent event = OutboxEvent.builder()
                .id("e1").channel(OutboxChannel.TELEGRAM)
                .type("ORDER_CREATED").payload("{}")
                .status(OutboxStatus.PROCESSING).retryCount(0)
                .build();

        // First call returns event, second returns null (stop loop)
        when(mongoTemplate.findAndModify(any(), any(), any(), eq(OutboxEvent.class)))
                .thenReturn(event).thenReturn(null);

        outboxWorker.processOutbox();

        verify(telegramHandler).handle("ORDER_CREATED", "{}");
        verify(emailHandler, never()).handle(any(), any());
        // Event should be saved as SENT
        verify(mongoTemplate).save(argThat(saved ->
                saved instanceof OutboxEvent e && e.getStatus() == OutboxStatus.SENT));
    }

    @Test
    void processOutbox_emailEvent_delegatesToEmailHandler() {
        OutboxEvent event = OutboxEvent.builder()
                .id("e2").channel(OutboxChannel.EMAIL)
                .type("ORDER_CONFIRMED").payload("{}")
                .status(OutboxStatus.PROCESSING).retryCount(0)
                .build();

        when(mongoTemplate.findAndModify(any(), any(), any(), eq(OutboxEvent.class)))
                .thenReturn(event).thenReturn(null);

        outboxWorker.processOutbox();

        verify(emailHandler).handle("ORDER_CONFIRMED", "{}");
        verify(telegramHandler, never()).handle(any(), any());
    }

    @Test
    void processOutbox_handlerFails_incrementsRetryCount() {
        OutboxEvent event = OutboxEvent.builder()
                .id("e3").channel(OutboxChannel.TELEGRAM)
                .type("ORDER_CREATED").payload("{}")
                .status(OutboxStatus.PROCESSING).retryCount(0)
                .build();

        when(mongoTemplate.findAndModify(any(), any(), any(), eq(OutboxEvent.class)))
                .thenReturn(event).thenReturn(null);
        doThrow(new RuntimeException("Telegram down")).when(telegramHandler).handle(any(), any());

        outboxWorker.processOutbox();

        verify(mongoTemplate).save(argThat(saved ->
                saved instanceof OutboxEvent e
                        && e.getRetryCount() == 1
                        && e.getStatus() == OutboxStatus.PENDING));
    }

    @Test
    void processOutbox_maxRetries_marksFailed() {
        OutboxEvent event = OutboxEvent.builder()
                .id("e4").channel(OutboxChannel.TELEGRAM)
                .type("ORDER_CREATED").payload("{}")
                .status(OutboxStatus.PROCESSING).retryCount(4)
                .build();

        when(mongoTemplate.findAndModify(any(), any(), any(), eq(OutboxEvent.class)))
                .thenReturn(event).thenReturn(null);
        doThrow(new RuntimeException("Still down")).when(telegramHandler).handle(any(), any());

        outboxWorker.processOutbox();

        verify(mongoTemplate).save(argThat(saved ->
                saved instanceof OutboxEvent e && e.getStatus() == OutboxStatus.FAILED));
    }

    @Test
    void processOutbox_noEvents_doesNothing() {
        when(mongoTemplate.findAndModify(any(), any(), any(), eq(OutboxEvent.class)))
                .thenReturn(null);

        outboxWorker.processOutbox();

        verify(telegramHandler, never()).handle(any(), any());
        verify(emailHandler, never()).handle(any(), any());
    }
}
