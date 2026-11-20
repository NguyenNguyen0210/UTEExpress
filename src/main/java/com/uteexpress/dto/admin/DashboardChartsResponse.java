package com.uteexpress.dto.admin;

import java.math.BigDecimal;
import java.util.List;

public record DashboardChartsResponse(
        String fromDate,
        String toDate,
        long totalShipments,
        long inProgressShipments,
        long deliveredShipments,
        long failedOrReturnedShipments,
        long cancelledShipments,
        BigDecimal totalRevenue,
        double deliverySuccessRate,
        BigDecimal pendingCodAmount,
        List<DailyMetricDto> dailyMetrics,
        StatusBreakdownDto statusBreakdown,
        List<HubPerformanceDto> hubPerformance
) {
    public record DailyMetricDto(
            String date,
            long shipmentCount,
            BigDecimal revenue
    ) {
    }

    public record StatusBreakdownDto(
            long delivered,
            long inProgress,
            long failedOrReturned,
            long cancelled
    ) {
    }

    public record HubPerformanceDto(
            String hubCode,
            String hubName,
            long originCount,
            long destCount,
            long deliveredCount,
            BigDecimal revenue
    ) {
    }
}
