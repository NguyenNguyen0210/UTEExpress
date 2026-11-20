package com.uteexpress.dto.customer;

import com.uteexpress.entity.Shipment;

import java.math.BigDecimal;
import java.util.List;

public record CustomerDashboardSummary(
        long totalShipments,
        long pendingPaymentCount,
        long inProgressCount,
        long deliveredCount,
        long failedOrReturnedCount,
        long cancelledCount,
        BigDecimal pendingCodAmount,
        List<Shipment> recentShipments
) {
}
