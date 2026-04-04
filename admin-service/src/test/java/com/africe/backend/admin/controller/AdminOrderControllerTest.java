package com.africe.backend.admin.controller;

import com.africe.backend.common.dto.OrderResponse;
import com.africe.backend.common.dto.UpdateOrderStatusRequest;
import com.africe.backend.common.model.Order;
import com.africe.backend.common.model.OrderStatus;
import com.africe.backend.order.repository.OrderRepository;
import com.africe.backend.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminOrderControllerTest {

    @Mock OrderRepository orderRepository;
    @Mock OrderService orderService;
    @InjectMocks AdminOrderController controller;

    @Test
    void listOrders_noFilters_returnsAll() {
        Page<Order> page = new PageImpl<>(List.of());
        when(orderRepository.findAll(any(Pageable.class))).thenReturn(page);

        Page<OrderResponse> result = controller.listOrders(null, null, 0, 20);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void listOrders_withStatusFilter() {
        Page<Order> page = new PageImpl<>(List.of());
        when(orderRepository.findByStatus(eq(OrderStatus.PENDING), any(Pageable.class)))
                .thenReturn(page);

        controller.listOrders(null, OrderStatus.PENDING, 0, 20);

        verify(orderRepository).findByStatus(eq(OrderStatus.PENDING), any(Pageable.class));
    }

    @Test
    void listOrders_withSearchFilter() {
        Page<Order> page = new PageImpl<>(List.of());
        when(orderRepository.findByEmailContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
                eq("john"), eq("john"), eq("john"), any(Pageable.class)))
                .thenReturn(page);

        controller.listOrders("john", null, 0, 20);

        verify(orderRepository)
                .findByEmailContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
                        eq("john"), eq("john"), eq("john"), any(Pageable.class));
    }

    @Test
    void updateStatus_delegatesToService() {
        OrderResponse expected = new OrderResponse(
                "o1", "John", "Doe", "j@t.com", "+380", List.of(),
                BigDecimal.valueOf(500), OrderStatus.CONFIRMED, null, null,
                null, null, null, null);
        when(orderService.updateStatus("o1", OrderStatus.CONFIRMED, null))
                .thenReturn(expected);

        OrderResponse result = controller.updateStatus("o1",
                new UpdateOrderStatusRequest(OrderStatus.CONFIRMED, null));

        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
    }
}
