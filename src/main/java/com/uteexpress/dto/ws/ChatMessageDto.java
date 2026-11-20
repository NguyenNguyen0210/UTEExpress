package com.uteexpress.dto.ws;

import com.uteexpress.entity.ChatMessage;

import java.time.OffsetDateTime;

public record ChatMessageDto(
        Long id,
        Long shipmentId,
        Long senderId,
        String senderName,
        String senderRole,
        String content,
        OffsetDateTime createdAt
) {

    public static ChatMessageDto from(ChatMessage message) {
        Long shipmentId = message.getShipment() != null ? message.getShipment().getId() : null;
        Long senderId = message.getSender() != null ? message.getSender().getId() : null;
        String senderName = message.getSender() != null ? message.getSender().getFullName() : "";
        String senderRole = (message.getSender() != null && message.getSender().getRole() != null)
                ? message.getSender().getRole().name()
                : "";
        return new ChatMessageDto(
                message.getId(),
                shipmentId,
                senderId,
                senderName,
                senderRole,
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
