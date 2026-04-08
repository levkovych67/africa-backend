package com.africe.backend.admin.controller;

import com.africe.backend.common.audit.AdminAudited;
import com.africe.backend.common.exception.ResourceNotFoundException;
import com.africe.backend.common.dto.OrderResponse;
import com.africe.backend.common.dto.UpdateOrderStatusRequest;
import com.africe.backend.common.model.Order;
import com.africe.backend.common.model.OrderStatus;
import com.africe.backend.order.repository.OrderRepository;
import com.africe.backend.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    public AdminOrderController(OrderRepository orderRepository, OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    @GetMapping
    public Page<OrderResponse> listOrders(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Order> orders;
        if (search != null && !search.isBlank()) {
            orders = orderRepository
                    .findByEmailContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
                            search, search, search, pageable);
        } else if (status != null) {
            orders = orderRepository.findByStatus(status, pageable);
        } else {
            orders = orderRepository.findAll(pageable);
        }
        return orders.map(orderService::toResponse);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable String id) {
        return orderService.getOrder(id);
    }

    @PutMapping("/{id}/status")
    @AdminAudited(action = "UPDATE_ORDER_STATUS")
    @CacheEvict(value = {"dashboardStats"}, allEntries = true)
    public OrderResponse updateStatus(@PathVariable String id,
                                      @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.updateStatus(id, request.status(), request.trackingNumber());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @AdminAudited(action = "DELETE_ORDER")
    @CacheEvict(value = {"dashboardStats"}, allEntries = true)
    public void deleteOrder(@PathVariable String id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        // Restore stock for orders where items haven't left the warehouse
        if (order.getStatus() != OrderStatus.SHIPPED
                && order.getStatus() != OrderStatus.DELIVERED
                && order.getStatus() != OrderStatus.CANCELLED) {
            orderService.restoreStock(order);
        }

        orderRepository.deleteById(id);
    }
}
