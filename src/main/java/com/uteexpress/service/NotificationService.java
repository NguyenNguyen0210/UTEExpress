package com.uteexpress.service;

import com.uteexpress.dto.ws.NotificationDto;
import com.uteexpress.dto.ws.NotificationListResponse;
import com.uteexpress.entity.Notification;
import com.uteexpress.entity.User;
import com.uteexpress.enums.Role;
import com.uteexpress.enums.UserStatus;
import com.uteexpress.exception.ResourceNotFoundException;
import com.uteexpress.repository.NotificationRepository;
import com.uteexpress.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public NotificationDto sendToUser(User recipient, String title, String message, String linkUrl) {
        if (recipient == null) {
            return null;
        }
        Notification notification = Notification.builder()
                .user(recipient)
                .title(title)
                .message(message)
                .linkUrl(linkUrl)
                .isRead(false)
                .createdAt(OffsetDateTime.now())
                .build();

        Notification saved = notificationRepository.save(notification);
        NotificationDto dto = toDto(saved);

        if (recipient.getEmail() != null && !recipient.getEmail().isBlank()) {
            messagingTemplate.convertAndSendToUser(
                    recipient.getEmail(),
                    "/queue/notifications",
                    dto
            );
        }
        return dto;
    }

    @Transactional
    public void sendToHubStaff(Long hubId, String title, String message, String linkUrl) {
        if (hubId == null) {
            return;
        }
        List<User> staffMembers = userRepository.findByHubIdAndRoleAndStatusOrderByFullNameAsc(
                hubId, Role.STAFF, UserStatus.ACTIVE);
        for (User staff : staffMembers) {
            sendToUser(staff, title, message, linkUrl);
        }
    }

    @Transactional(readOnly = true)
    public NotificationListResponse getRecentNotifications(Long userId) {
        long unreadCount = notificationRepository.countByUserIdAndIsReadFalse(userId);
        List<NotificationDto> notifications = notificationRepository
                .findTop20ByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDto)
                .toList();
        return new NotificationListResponse(unreadCount, notifications);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông báo"));
        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }

    private NotificationDto toDto(Notification notification) {
        return new NotificationDto(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getLinkUrl(),
                Boolean.TRUE.equals(notification.getIsRead()),
                notification.getCreatedAt()
        );
    }
}
