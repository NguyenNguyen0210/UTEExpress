package com.uteexpress.dto.ws;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

public record NotificationDto(
        Long id,
        String title,
        String message,
        String linkUrl,
        @JsonProperty("isRead") boolean isRead,
        OffsetDateTime createdAt
) {
}
