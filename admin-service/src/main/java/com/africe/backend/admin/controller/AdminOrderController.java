package com.africe.backend.admin.controller;

import com.africe.backend.common.audit.AdminAudited;
import com.africe.backend.common.dto.OrderResponse;
import com.africe.backend.common.dto.UpdateOrderStatusRequest;
import com.africe.backend.common.model.OrderStatus;
import com.africe.backend.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    public Page<OrderResponse> listOrders(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) OrderStatus status,
            Pageable pageable) {
        return orderService.listOrders(search, status, pageable);
    }

    @PutMapping("/{id}/status")
    @AdminAudited(action = "UPDATE_ORDER_STATUS")
    public OrderResponse updateStatus(@PathVariable String id,
                                      @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.updateStatus(id, request.getStatus());
    }
}
