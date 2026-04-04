package com.africe.backend.order.controller;

import com.africe.backend.common.dto.CheckoutRequest;
import com.africe.backend.common.dto.OrderResponse;
import com.africe.backend.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
public class CheckoutController {

    private final OrderService orderService;

    public CheckoutController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(@Valid @RequestBody CheckoutRequest request) {
        return orderService.checkout(request);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable String id,
                                  @RequestParam(required = false) String token) {
        return orderService.getOrder(id, token);
    }
}
