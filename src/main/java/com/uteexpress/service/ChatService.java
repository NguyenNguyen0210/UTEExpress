package com.uteexpress.service;

import com.uteexpress.dto.ws.ChatMessageDto;
import com.uteexpress.entity.ChatMessage;
import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.User;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.exception.ResourceNotFoundException;
import com.uteexpress.repository.ChatMessageRepository;
import com.uteexpress.repository.ShipmentRepository;
import com.uteexpress.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ChatService {

    public static final int MAX_MESSAGE_LENGTH = 2000;

    private final ChatMessageRepository chatMessageRepository;
    private final ShipmentRepository shipmentRepository;
    private final UserRepository userRepository;

    public boolean canAccessChat(Shipment shipment, User user) {
        if (shipment == null || user == null || user.getRole() == null) {
            return false;
        }

        return switch (user.getRole()) {
            case ADMIN -> true;
            case CUSTOMER -> shipment.getCustomer() != null
                    && user.getId() != null
                    && Objects.equals(shipment.getCustomer().getId(), user.getId());
            case STAFF -> isStaffInShipmentHubScope(shipment, user);
            case SHIPPER -> false;
        };
    }

    public void assertCanAccessChat(Shipment shipment, User user) {
        if (!canAccessChat(shipment, user)) {
            throw new AccessDeniedException("Bạn không có quyền truy cập phòng chat của vận đơn này");
        }
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessages(Long shipmentId, User actor) {
        Shipment shipment = loadShipment(shipmentId);
        assertCanAccessChat(shipment, actor);

        return chatMessageRepository.findByShipmentIdOrderByCreatedAtAscIdAsc(shipmentId)
                .stream()
                .map(ChatMessageDto::from)
                .toList();
    }

    @Transactional
    public ChatMessageDto sendMessage(Long shipmentId, User sender, String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException("Nội dung tin nhắn không được để trống");
        }
        String trimmedContent = content.trim();
        if (trimmedContent.length() > MAX_MESSAGE_LENGTH) {
            throw new BusinessException("Nội dung tin nhắn không được vượt quá " + MAX_MESSAGE_LENGTH + " ký tự");
        }

        Shipment shipment = loadShipment(shipmentId);
        assertCanAccessChat(shipment, sender);

        ChatMessage chatMessage = ChatMessage.builder()
                .shipment(shipment)
                .sender(sender)
                .content(trimmedContent)
                .createdAt(OffsetDateTime.now())
                .build();

        ChatMessage saved = chatMessageRepository.save(chatMessage);
        return ChatMessageDto.from(saved);
    }

    @Transactional(readOnly = true)
    public User resolveUserById(Long userId) {
        if (userId == null) {
            throw new AccessDeniedException("Phiên đăng nhập không hợp lệ");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new AccessDeniedException("Không tìm thấy tài khoản người dùng"));
    }

    @Transactional(readOnly = true)
    public User resolveUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new AccessDeniedException("Phiên đăng nhập không hợp lệ");
        }
        return userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new AccessDeniedException("Không tìm thấy tài khoản người dùng"));
    }

    private Shipment loadShipment(Long shipmentId) {
        if (shipmentId == null) {
            throw new ResourceNotFoundException("Mã vận đơn không hợp lệ");
        }
        return shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vận đơn với ID: " + shipmentId));
    }

    private boolean isStaffInShipmentHubScope(Shipment shipment, User staff) {
        if (staff.getHub() == null || staff.getHub().getId() == null) {
            return false;
        }
        Long staffHubId = staff.getHub().getId();
        boolean matchesOrigin = shipment.getOriginHub() != null
                && Objects.equals(shipment.getOriginHub().getId(), staffHubId);
        boolean matchesDest = shipment.getDestHub() != null
                && Objects.equals(shipment.getDestHub().getId(), staffHubId);
        boolean matchesCurrent = shipment.getCurrentHub() != null
                && Objects.equals(shipment.getCurrentHub().getId(), staffHubId);
        return matchesOrigin || matchesDest || matchesCurrent;
    }
}
