package com.uteexpress.controller.ws;

import com.uteexpress.dto.ws.ChatMessageDto;
import com.uteexpress.dto.ws.ChatSendPayload;
import com.uteexpress.entity.User;
import com.uteexpress.repository.UserRepository;
import com.uteexpress.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat/{shipmentId}")
    public void sendChatMessage(@DestinationVariable Long shipmentId,
                                @Payload ChatSendPayload payload,
                                Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new AccessDeniedException("Bạn cần đăng nhập để gửi tin nhắn CSKH");
        }

        User sender = userRepository.findByEmailIgnoreCase(principal.getName())
                .orElseThrow(() -> new AccessDeniedException("Không tìm thấy tài khoản người gửi"));

        String content = payload != null ? payload.content() : null;
        ChatMessageDto dto = chatService.sendMessage(shipmentId, sender, content);
        messagingTemplate.convertAndSend("/topic/chat/" + shipmentId, dto);
    }
}
