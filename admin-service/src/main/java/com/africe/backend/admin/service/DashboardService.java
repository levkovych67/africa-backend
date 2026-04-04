package com.africe.backend.admin.service;

import com.africe.backend.common.dto.DashboardStatsResponse;
import com.africe.backend.common.dto.RevenueDayDto;
import com.africe.backend.common.dto.TopProductDto;
import com.africe.backend.common.model.Order;
import com.africe.backend.common.model.OrderItem;
import com.africe.backend.common.model.OrderStatus;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class DashboardService {

    private final MongoTemplate mongoTemplate;

    public DashboardService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Cacheable(value = "dashboardStats", key = "#from + '-' + #to")
    public DashboardStatsResponse getStats(LocalDate from, LocalDate to) {
        Instant fromInstant = from.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        Criteria dateCriteria = Criteria.where("createdAt").gte(fromInstant).lt(toInstant)
                .and("status").nin(OrderStatus.CANCELLED, OrderStatus.WAITING_PAYMENT);

        // Get orders in range
        List<Order> orders = mongoTemplate.find(
                new org.springframework.data.mongodb.core.query.Query(dateCriteria), Order.class);

        BigDecimal totalRevenue = orders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalOrders = orders.size();

        // Top products by quantity
        List<TopProductDto> topProducts = orders.stream()
                .flatMap(o -> o.getItems().stream())
                .collect(java.util.stream.Collectors.groupingBy(
                        OrderItem::getProductId,
                        java.util.stream.Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    List<OrderItem> items = entry.getValue();
                    String title = items.getFirst().getProductTitle();
                    long qty = items.stream().mapToLong(OrderItem::getQuantity).sum();
                    BigDecimal rev = items.stream()
                            .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new TopProductDto(entry.getKey(), title, qty, rev);
                })
                .sorted((a, b) -> Long.compare(b.totalQuantity(), a.totalQuantity()))
                .limit(10)
                .toList();

        // Revenue by day
        List<RevenueDayDto> revenueByDay = orders.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        o -> o.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate()))
                .entrySet().stream()
                .map(entry -> new RevenueDayDto(
                        entry.getKey(),
                        entry.getValue().stream()
                                .map(Order::getTotalAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .sorted(java.util.Comparator.comparing(RevenueDayDto::date))
                .toList();

        return new DashboardStatsResponse(totalRevenue, totalOrders, topProducts, revenueByDay);
    }
}
