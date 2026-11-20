package com.uteexpress.service;

import com.uteexpress.dto.publicview.TrackingUpdateEvent;
import com.uteexpress.dto.ws.ShipmentStatusChangedEvent;
import com.uteexpress.enums.ShipmentStatus;
import com.uteexpress.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShipmentRealtimeEventListener {

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onShipmentStatusChanged(ShipmentStatusChangedEvent event) {
        if (event == null) {
            return;
        }
        broadcastTrackingUpdate(event);
        dispatchRoleNotifications(event);
    }

    private void broadcastTrackingUpdate(ShipmentStatusChangedEvent event) {
        if (event.trackingCode() == null || event.trackingCode().isBlank()) {
            return;
        }
        try {
            ShipmentStatus status = event.status();
            String statusCode = status != null ? status.name() : "";
            String statusLabel = status != null ? status.getLabel() : "";
            String currentHubName = event.currentHubName() != null ? event.currentHubName() : "";
            String logTitle = (event.logTitle() != null && !event.logTitle().isBlank())
                    ? event.logTitle()
                    : statusLabel;
            String logDescription = event.logDescription() != null ? event.logDescription() : "";
            OffsetDateTime timestamp = event.timestamp() != null ? event.timestamp() : OffsetDateTime.now();

            TrackingUpdateEvent payload = new TrackingUpdateEvent(
                    event.trackingCode(),
                    statusCode,
                    statusLabel,
                    currentHubName,
                    logTitle,
                    logDescription,
                    timestamp
            );

            messagingTemplate.convertAndSend("/topic/tracking/" + event.trackingCode(), payload);
        } catch (Exception ex) {
            log.warn("Failed to broadcast live tracking update for trackingCode={}: {}",
                    event.trackingCode(), ex.getMessage());
        }
    }

    private void dispatchRoleNotifications(ShipmentStatusChangedEvent event) {
        ShipmentStatus status = event.status();
        if (status == null) {
            return;
        }

        String code = event.trackingCode() != null ? event.trackingCode() : "";
        String staffLink = event.shipmentId() != null
                ? "/staff/shipments/" + event.shipmentId()
                : "/staff/shipments";
        String customerLink = event.shipmentId() != null
                ? "/customer/shipments/" + event.shipmentId()
                : "/customer/shipments";
        String hubDisplay = (event.currentHubName() != null && !event.currentHubName().isBlank())
                ? event.currentHubName()
                : null;

        switch (status) {
            case PENDING_PAYMENT -> {
                // Online payment pending; hub staff notification triggers upon CREATED.
            }
            case CREATED -> notifyHubStaff(
                    event.originHubId(),
                    "Đơn hàng mới cần điều phối",
                    "Vận đơn " + code + " vừa được tạo và sẵn sàng phân công lấy hàng.",
                    staffLink
            );
            case ASSIGNED_PICKUP -> {
                notifyUserById(
                        event.pickupShipperId(),
                        "Nhiệm vụ lấy hàng mới",
                        "Bạn được phân công đến lấy vận đơn " + code + ".",
                        "/shipper/tasks?tab=pickup"
                );
                notifyUserById(
                        event.customerId(),
                        "Đã phân công Shipper lấy hàng",
                        "Vận đơn " + code + " đã được phân công Shipper đến lấy hàng.",
                        customerLink
                );
            }
            case PICKED_UP -> {
                notifyUserById(
                        event.customerId(),
                        "Shipper đã lấy hàng",
                        "Shipper đã lấy thành công kiện hàng của vận đơn " + code + ".",
                        customerLink
                );
                notifyHubStaff(
                        event.originHubId(),
                        "Shipper đã lấy hàng về bưu cục",
                        "Vận đơn " + code + " đã được Shipper lấy hàng và đang chuyển về bưu cục gửi.",
                        staffLink
                );
            }
            case IN_ORIGIN_HUB -> notifyUserById(
                    event.customerId(),
                    "Đã nhập kho bưu cục gửi",
                    "Vận đơn " + code + " đã được nhập kho tại "
                            + (hubDisplay != null ? hubDisplay : "bưu cục gửi") + ".",
                    customerLink
            );
            case IN_TRANSIT -> {
                notifyHubStaff(
                        event.destHubId(),
                        "Hàng trung chuyển đang đến",
                        "Vận đơn " + code + " đang được trung chuyển đến bưu cục nhận.",
                        staffLink
                );
                notifyUserById(
                        event.customerId(),
                        "Đơn hàng đang trung chuyển",
                        "Vận đơn " + code + " đang trên đường luân chuyển liên tỉnh đến bưu cục nhận.",
                        customerLink
                );
            }
            case IN_DEST_HUB -> {
                notifyUserById(
                        event.customerId(),
                        "Đã đến bưu cục nhận",
                        "Vận đơn " + code + " đã sẵn sàng phân công giao hàng tại "
                                + (hubDisplay != null ? hubDisplay : "bưu cục nhận") + ".",
                        customerLink
                );
                if (event.originHubId() != null && Objects.equals(event.originHubId(), event.destHubId())) {
                    notifyHubStaff(
                            event.destHubId(),
                            "Đơn nội vùng sẵn sàng phát",
                            "Vận đơn nội vùng " + code + " đã sẵn sàng phân công Shipper giao hàng.",
                            staffLink
                    );
                }
            }
            case OUT_FOR_DELIVERY -> {
                notifyUserById(
                        event.deliveryShipperId(),
                        "Nhiệm vụ giao hàng mới",
                        "Bạn được phân công đi giao vận đơn " + code + ".",
                        "/shipper/tasks?tab=delivery"
                );
                notifyUserById(
                        event.customerId(),
                        "Đơn hàng đang được giao",
                        "Shipper đang trên đường giao vận đơn " + code + " đến người nhận.",
                        customerLink
                );
            }
            case DELIVERED -> notifyUserById(
                    event.customerId(),
                    "Giao hàng thành công",
                    "Vận đơn " + code + " đã được giao thành công đến người nhận.",
                    customerLink
            );
            case DELIVERY_FAILED -> {
                String reasonSuffix = (event.logDescription() != null && !event.logDescription().isBlank())
                        ? " " + event.logDescription()
                        : "";
                notifyHubStaff(
                        event.destHubId(),
                        "Giao hàng thất bại",
                        "Vận đơn " + code + " giao hàng thất bại." + reasonSuffix,
                        staffLink
                );
                notifyUserById(
                        event.customerId(),
                        "Giao hàng thất bại",
                        "Vận đơn " + code + " giao không thành công." + reasonSuffix,
                        customerLink
                );
            }
            case RETURNING -> notifyUserById(
                    event.customerId(),
                    "Đơn hàng đang hoàn trả",
                    "Vận đơn " + code + " đang trong quá trình hoàn hàng về người gửi.",
                    customerLink
            );
            case RETURNED -> notifyUserById(
                    event.customerId(),
                    "Đã hoàn hàng về người gửi",
                    "Vận đơn " + code + " đã được hoàn trả thành công về người gửi.",
                    customerLink
            );
            case CANCELLED -> notifyUserById(
                    event.customerId(),
                    "Vận đơn đã bị hủy",
                    "Vận đơn " + code + " đã được hủy.",
                    customerLink
            );
        }
    }

    private void notifyUserById(Long userId, String title, String message, String linkUrl) {
        if (userId == null) {
            return;
        }
        try {
            userRepository.findById(userId)
                    .ifPresent(user -> notificationService.sendToUser(user, title, message, linkUrl));
        } catch (Exception ex) {
            log.warn("Failed to send notification to userId={}: {}", userId, ex.getMessage());
        }
    }

    private void notifyHubStaff(Long hubId, String title, String message, String linkUrl) {
        if (hubId == null) {
            return;
        }
        try {
            notificationService.sendToHubStaff(hubId, title, message, linkUrl);
        } catch (Exception ex) {
            log.warn("Failed to send notification to hub staff hubId={}: {}", hubId, ex.getMessage());
        }
    }
}
