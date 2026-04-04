package com.africe.backend.admin.service;

import com.africe.backend.common.dto.DashboardStatsResponse;
import com.africe.backend.common.model.Order;
import com.africe.backend.common.model.OrderItem;
import com.africe.backend.common.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock MongoTemplate mongoTemplate;
    @InjectMocks DashboardService dashboardService;

    @Test
    void getStats_emptyOrders() {
        when(mongoTemplate.find(any(), eq(Order.class))).thenReturn(List.of());

        DashboardStatsResponse stats = dashboardService.getStats(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(stats.totalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(stats.totalOrders()).isZero();
        assertThat(stats.topProducts()).isEmpty();
        assertThat(stats.revenueByDay()).isEmpty();
    }

    @Test
    void getStats_calculatesCorrectly() {
        OrderItem item = OrderItem.builder()
                .productId("p1").productTitle("T-Shirt").sku("S")
                .quantity(2).unitPrice(BigDecimal.valueOf(500)).build();
        Order order = Order.builder()
                .id("o1").status(OrderStatus.CONFIRMED)
                .totalAmount(BigDecimal.valueOf(1000))
                .items(List.of(item))
                .createdAt(Instant.parse("2026-06-15T10:00:00Z"))
                .build();

        when(mongoTemplate.find(any(), eq(Order.class))).thenReturn(List.of(order));

        DashboardStatsResponse stats = dashboardService.getStats(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(stats.totalRevenue()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(stats.totalOrders()).isEqualTo(1);
        assertThat(stats.topProducts()).hasSize(1);
        assertThat(stats.topProducts().getFirst().productTitle()).isEqualTo("T-Shirt");
        assertThat(stats.topProducts().getFirst().totalQuantity()).isEqualTo(2);
        assertThat(stats.revenueByDay()).hasSize(1);
    }
}
