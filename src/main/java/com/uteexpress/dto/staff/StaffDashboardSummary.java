package com.uteexpress.dto.staff;

import com.uteexpress.entity.Shipment;

import java.math.BigDecimal;
import java.util.List;

public record StaffDashboardSummary(
        String hubLabel,
        long waitingPickupCount,
        long inOriginHubCount,
        long incomingTransitCount,
        long readyForDeliveryCount,
        long failedOrReturningCount,
        long heldCodShipmentCount,
        BigDecimal heldCodAmount,
        List<Shipment> urgentShipments
) {
}
