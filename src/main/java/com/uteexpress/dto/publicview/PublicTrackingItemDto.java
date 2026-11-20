package com.uteexpress.dto.publicview;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Masked public shipment tracking view per BR-11.
 * Intentionally excludes codAmount, podImageKey, full phone numbers, and street addresses.
 */
public record PublicTrackingItemDto(
        String trackingCode,
        String status,
        String statusLabel,
        String statusBadgeClass,
        int progressStep,
        String serviceName,
        String originHubCode,
        String originHubName,
        String destHubCode,
        String destHubName,
        String currentHubName,
        String maskedSenderName,
        String maskedSenderPhone,
        String senderRegion,
        String maskedReceiverName,
        String maskedReceiverPhone,
        String receiverRegion,
        String itemName,
        BigDecimal chargeableWeightKg,
        boolean fragile,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<PublicTrackingLogDto> logs
) {
}
