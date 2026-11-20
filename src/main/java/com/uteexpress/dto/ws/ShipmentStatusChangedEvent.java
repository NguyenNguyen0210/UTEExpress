package com.uteexpress.dto.ws;

import com.uteexpress.entity.Hub;
import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.ShipmentTrackingLog;
import com.uteexpress.enums.ShipmentStatus;

import java.time.OffsetDateTime;
import java.util.Objects;

public record ShipmentStatusChangedEvent(
        Long shipmentId,
        String trackingCode,
        ShipmentStatus status,
        Long originHubId,
        Long destHubId,
        Long currentHubId,
        String currentHubName,
        Long customerId,
        Long pickupShipperId,
        Long deliveryShipperId,
        String logTitle,
        String logDescription,
        OffsetDateTime timestamp
) {

    public static ShipmentStatusChangedEvent from(Shipment shipment, ShipmentTrackingLog log) {
        Objects.requireNonNull(shipment, "shipment must not be null");

        ShipmentStatus resolvedStatus = (log != null && log.getStatus() != null)
                ? log.getStatus()
                : shipment.getStatus();

        Hub resolvedHub = (log != null && log.getHub() != null)
                ? log.getHub()
                : shipment.getCurrentHub();

        Long originHubId = shipment.getOriginHub() != null ? shipment.getOriginHub().getId() : null;
        Long destHubId = shipment.getDestHub() != null ? shipment.getDestHub().getId() : null;
        Long currentHubId = resolvedHub != null ? resolvedHub.getId() : null;
        String currentHubName = formatHubDisplay(resolvedHub);

        Long customerId = shipment.getCustomer() != null ? shipment.getCustomer().getId() : null;
        Long pickupShipperId = shipment.getPickupShipper() != null ? shipment.getPickupShipper().getId() : null;
        Long deliveryShipperId = shipment.getDeliveryShipper() != null ? shipment.getDeliveryShipper().getId() : null;

        String statusLabel = resolvedStatus != null ? resolvedStatus.getLabel() : "";
        String logTitle = (log != null && log.getTitle() != null && !log.getTitle().isBlank())
                ? log.getTitle()
                : statusLabel;
        String logDescription = (log != null && log.getDescription() != null)
                ? log.getDescription()
                : "";
        OffsetDateTime timestamp = (log != null && log.getCreatedAt() != null)
                ? log.getCreatedAt()
                : OffsetDateTime.now();

        return new ShipmentStatusChangedEvent(
                shipment.getId(),
                shipment.getTrackingCode(),
                resolvedStatus,
                originHubId,
                destHubId,
                currentHubId,
                currentHubName,
                customerId,
                pickupShipperId,
                deliveryShipperId,
                logTitle,
                logDescription,
                timestamp
        );
    }

    private static String formatHubDisplay(Hub hub) {
        if (hub == null) {
            return "";
        }
        String name = hub.getName() != null ? hub.getName() : "";
        if (hub.getCode() != null && !hub.getCode().isBlank()) {
            return name.isBlank() ? hub.getCode() : name + " (" + hub.getCode() + ")";
        }
        return name;
    }
}
