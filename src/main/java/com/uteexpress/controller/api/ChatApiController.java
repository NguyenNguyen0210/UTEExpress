package com.uteexpress.controller.api;

import com.uteexpress.dto.ws.ChatMessageDto;
import com.uteexpress.entity.User;
import com.uteexpress.security.CustomUserDetails;
import com.uteexpress.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatApiController {

    private final ChatService chatService;

    @GetMapping("/{shipmentId}/messages")
    public ResponseEntity<List<ChatMessageDto>> getMessages(
            @PathVariable Long shipmentId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new AccessDeniedException("Chưa đăng nhập hoặc phiên làm việc đã hết hạn");
        }
        User actor = chatService.resolveUserById(currentUser.getId());
        return ResponseEntity.ok(chatService.getMessages(shipmentId, actor));
    }
}
