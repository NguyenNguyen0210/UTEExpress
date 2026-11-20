package com.uteexpress.dto.publicview;

import java.time.OffsetDateTime;

public record TrackingUpdateEvent(
        String trackingCode,
        String status,
        String statusLabel,
        String currentHubName,
        String logTitle,
        String logDescription,
        OffsetDateTime timestamp
) {
}
