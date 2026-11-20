package com.uteexpress.dto.publicview;

import java.time.OffsetDateTime;

public record PublicTrackingLogDto(
        String status,
        String statusLabel,
        String hubName,
        String title,
        String description,
        OffsetDateTime createdAt
) {
}
