package com.africe.backend.common.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardStatsResponse(
        BigDecimal totalRevenue,
        long totalOrders,
        List<TopProductDto> topProducts,
        List<RevenueDayDto> revenueByDay
) {}
