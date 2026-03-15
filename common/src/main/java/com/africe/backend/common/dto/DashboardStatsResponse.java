package com.africe.backend.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {

    private BigDecimal totalRevenue;
    private long totalOrders;
    private long totalUnitsSold;
    private List<TopProductDto> topProducts;
    private List<RevenueDayDto> revenueByDay;
}
