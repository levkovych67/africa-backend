package com.africe.backend.admin.controller;

import com.africe.backend.common.dto.DashboardStatsResponse;
import com.africe.backend.common.dto.RevenueDayDto;
import com.africe.backend.common.dto.TopProductDto;
import com.africe.backend.common.model.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class DashboardController {

    private final MongoTemplate mongoTemplate;

    @GetMapping("/stats")
    public DashboardStatsResponse getStats(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {

        if (from == null) {
            from = LocalDate.now().minusDays(30);
        }
        if (to == null) {
            to = LocalDate.now();
        }

        Instant fromInstant = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toInstant = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<String> validStatuses = Arrays.asList(
                OrderStatus.CONFIRMED.name(),
                OrderStatus.SHIPPED.name(),
                OrderStatus.DELIVERED.name()
        );

        // Build faceted aggregation using raw Document for $facet
        Document matchStage = new Document("$match", new Document()
                .append("createdAt", new Document("$gte", fromInstant).append("$lt", toInstant))
                .append("status", new Document("$in", validStatuses)));

        Document facetStage = new Document("$facet", new Document()
                .append("totalRevenue", Arrays.asList(
                        new Document("$group", new Document("_id", null)
                                .append("value", new Document("$sum", "$totalAmount")))
                ))
                .append("totalOrders", Arrays.asList(
                        new Document("$count", "value")
                ))
                .append("totalUnitsSold", Arrays.asList(
                        new Document("$unwind", "$items"),
                        new Document("$group", new Document("_id", null)
                                .append("value", new Document("$sum", "$items.quantity")))
                ))
                .append("topProducts", Arrays.asList(
                        new Document("$unwind", "$items"),
                        new Document("$group", new Document("_id", "$items.productId")
                                .append("title", new Document("$first", "$items.productTitle"))
                                .append("unitsSold", new Document("$sum", "$items.quantity"))
                                .append("revenue", new Document("$sum",
                                        new Document("$multiply", Arrays.asList("$items.unitPrice", "$items.quantity"))))),
                        new Document("$sort", new Document("unitsSold", -1)),
                        new Document("$limit", 5)
                ))
                .append("revenueByDay", Arrays.asList(
                        new Document("$group", new Document("_id",
                                new Document("$dateToString", new Document("format", "%Y-%m-%d").append("date", "$createdAt")))
                                .append("revenue", new Document("$sum", "$totalAmount"))
                                .append("orders", new Document("$sum", 1))),
                        new Document("$sort", new Document("_id", 1))
                ))
        );

        List<Document> pipeline = Arrays.asList(matchStage, facetStage);
        List<Document> resultList = mongoTemplate.getCollection("orders")
                .aggregate(pipeline)
                .into(new ArrayList<>());

        Document facetResult = resultList.isEmpty()
                ? new Document()
                : resultList.get(0);

        // Parse totalRevenue
        BigDecimal totalRevenue = BigDecimal.ZERO;
        List<Document> revenueList = facetResult.getList("totalRevenue", Document.class, Collections.emptyList());
        if (!revenueList.isEmpty()) {
            Object val = revenueList.get(0).get("value");
            totalRevenue = toBigDecimal(val);
        }

        // Parse totalOrders
        long totalOrders = 0;
        List<Document> ordersList = facetResult.getList("totalOrders", Document.class, Collections.emptyList());
        if (!ordersList.isEmpty()) {
            totalOrders = ordersList.get(0).getInteger("value", 0);
        }

        // Parse totalUnitsSold
        long totalUnitsSold = 0;
        List<Document> unitsList = facetResult.getList("totalUnitsSold", Document.class, Collections.emptyList());
        if (!unitsList.isEmpty()) {
            Object val = unitsList.get(0).get("value");
            totalUnitsSold = val instanceof Number ? ((Number) val).longValue() : 0;
        }

        // Parse topProducts
        List<Document> topProductsDocs = facetResult.getList("topProducts", Document.class, Collections.emptyList());
        List<TopProductDto> topProducts = topProductsDocs.stream()
                .map(doc -> TopProductDto.builder()
                        .productId(doc.getString("_id"))
                        .title(doc.getString("title"))
                        .unitsSold(doc.get("unitsSold") instanceof Number ? ((Number) doc.get("unitsSold")).longValue() : 0)
                        .revenue(toBigDecimal(doc.get("revenue")))
                        .build())
                .collect(Collectors.toList());

        // Parse revenueByDay
        List<Document> revenueByDayDocs = facetResult.getList("revenueByDay", Document.class, Collections.emptyList());
        List<RevenueDayDto> revenueByDay = revenueByDayDocs.stream()
                .map(doc -> RevenueDayDto.builder()
                        .date(doc.getString("_id"))
                        .revenue(toBigDecimal(doc.get("revenue")))
                        .orders(doc.get("orders") instanceof Number ? ((Number) doc.get("orders")).longValue() : 0)
                        .build())
                .collect(Collectors.toList());

        return DashboardStatsResponse.builder()
                .totalRevenue(totalRevenue)
                .totalOrders(totalOrders)
                .totalUnitsSold(totalUnitsSold)
                .topProducts(topProducts)
                .revenueByDay(revenueByDay)
                .build();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof org.bson.types.Decimal128) {
            return ((org.bson.types.Decimal128) value).bigDecimalValue();
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        return BigDecimal.ZERO;
    }
}
