package com.africe.backend.product.controller;

import com.africe.backend.product.service.ProductEventService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/products")
public class ProductEventController {

    private final ProductEventService productEventService;

    public ProductEventController(ProductEventService productEventService) {
        this.productEventService = productEventService;
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        return productEventService.subscribe();
    }
}
