package com.africe.backend.product.service;

import com.africe.backend.common.dto.ProductUpdateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class ProductEventService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper;

    public ProductEventService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L); // no timeout — browser reconnects on disconnect

        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        return emitter;
    }

    public void broadcast(ProductUpdateEvent event) {
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (IOException e) {
            log.error("Failed to serialize ProductUpdateEvent", e);
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("product-update")
                        .data(json));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }
}
