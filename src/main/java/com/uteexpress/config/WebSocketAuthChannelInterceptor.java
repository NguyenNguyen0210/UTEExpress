package com.uteexpress.config;

import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.User;
import com.uteexpress.enums.UserStatus;
import com.uteexpress.repository.ShipmentRepository;
import com.uteexpress.repository.UserRepository;
import com.uteexpress.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern CHAT_DESTINATION_PATTERN = Pattern.compile("^/(?:topic|app)/chat/(\\d+)$");

    private final ChatService chatService;
    private final ShipmentRepository shipmentRepository;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command != StompCommand.SUBSCRIBE && command != StompCommand.SEND) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null || destination.isBlank()) {
            return message;
        }

        // Public read-only live tracking channel
        if (destination.startsWith("/topic/tracking/")) {
            if (command == StompCommand.SEND) {
                throw new AccessDeniedException("Kênh theo dõi hành trình chỉ cho phép nhận dữ liệu");
            }
            return message;
        }

        // Personal user queues require authenticated user
        if (destination.startsWith("/user/queue/")) {
            requireAuthenticatedPrincipal(accessor);
            return message;
        }

        // CSKH Chat destinations: /topic/chat/{id} and /app/chat/{id}
        if (destination.startsWith("/topic/chat/") || destination.startsWith("/app/chat/")) {
            Matcher matcher = CHAT_DESTINATION_PATTERN.matcher(destination);
            if (!matcher.matches()) {
                throw new AccessDeniedException("Đường dẫn phòng chat không hợp lệ");
            }

            Principal principal = requireAuthenticatedPrincipal(accessor);
            Long shipmentId = Long.parseLong(matcher.group(1));

            User user = userRepository.findByEmailIgnoreCase(principal.getName())
                    .orElseThrow(() -> new AccessDeniedException("Tài khoản không hợp lệ"));
            if (user.getStatus() == UserStatus.LOCKED) {
                throw new AccessDeniedException("Tài khoản đã bị khóa");
            }

            Shipment shipment = shipmentRepository.findById(shipmentId)
                    .orElseThrow(() -> new AccessDeniedException("Không tìm thấy vận đơn hoặc không có quyền truy cập phòng chat"));

            chatService.assertCanAccessChat(shipment, user);
            return message;
        }

        return message;
    }

    private Principal requireAuthenticatedPrincipal(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal == null
                || principal instanceof AnonymousAuthenticationToken
                || principal.getName() == null
                || principal.getName().isBlank()
                || "anonymousUser".equalsIgnoreCase(principal.getName())) {
            throw new AccessDeniedException("Bạn cần đăng nhập để truy cập kênh WebSocket này");
        }
        return principal;
    }
}
