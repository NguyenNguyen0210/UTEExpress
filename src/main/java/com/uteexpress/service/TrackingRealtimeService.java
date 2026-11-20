package com.uteexpress.service;

import com.uteexpress.dto.publicview.TrackingUpdateEvent;
import com.uteexpress.entity.Hub;
import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.ShipmentTrackingLog;
import com.uteexpress.enums.ShipmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class TrackingRealtimeService {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastStatusUpdate(Shipment shipment, ShipmentTrackingLog log) {
        if (shipment == null || shipment.getTrackingCode() == null || shipment.getTrackingCode().isBlank()) {
            return;
        }

        ShipmentStatus status = (log != null && log.getStatus() != null)
                ? log.getStatus()
                : shipment.getStatus();
        Hub hub = (log != null && log.getHub() != null)
                ? log.getHub()
                : shipment.getCurrentHub();

        String statusCode = status != null ? status.name() : "";
        String statusLabel = status != null ? status.getLabel() : "";
        String currentHubName = formatHubDisplay(hub);
        String logTitle = (log != null && log.getTitle() != null && !log.getTitle().isBlank())
                ? log.getTitle()
                : statusLabel;
        String logDescription = (log != null && log.getDescription() != null)
                ? log.getDescription()
                : "";
        OffsetDateTime timestamp = (log != null && log.getCreatedAt() != null)
                ? log.getCreatedAt()
                : OffsetDateTime.now();

        TrackingUpdateEvent event = new TrackingUpdateEvent(
                shipment.getTrackingCode(),
                statusCode,
                statusLabel,
                currentHubName,
                logTitle,
                logDescription,
                timestamp
        );

        messagingTemplate.convertAndSend("/topic/tracking/" + shipment.getTrackingCode(), event);
    }

    private String formatHubDisplay(Hub hub) {
        if (hub == null) {
            return "";
        }
        if (hub.getCode() != null && !hub.getCode().isBlank()) {
            return hub.getName() + " (" + hub.getCode() + ")";
        }
        return hub.getName();
    }
}
