package com.africe.backend.common.event;

import org.springframework.context.ApplicationEvent;

public class OutboxEventCreated extends ApplicationEvent {
    public OutboxEventCreated(Object source) {
        super(source);
    }
}
