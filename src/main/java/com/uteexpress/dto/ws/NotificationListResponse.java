package com.uteexpress.dto.ws;

import java.util.List;

public record NotificationListResponse(
        long unreadCount,
        List<NotificationDto> notifications
) {
}
