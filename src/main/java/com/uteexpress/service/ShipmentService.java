package com.uteexpress.service;

import com.uteexpress.dto.ws.ShipmentStatusChangedEvent;
import com.uteexpress.entity.Hub;
import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.ShipmentTrackingLog;
import com.uteexpress.entity.User;
import com.uteexpress.enums.CodStatus;
import com.uteexpress.enums.PayerType;
import com.uteexpress.enums.PaymentStatus;
import com.uteexpress.enums.Role;
import com.uteexpress.enums.ShipmentStatus;
import com.uteexpress.enums.UserStatus;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.exception.ResourceNotFoundException;
import com.uteexpress.repository.ShipmentRepository;
import com.uteexpress.repository.ShipmentTrackingLogRepository;
import com.uteexpress.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentTrackingLogRepository trackingLogRepository;
    private final UserRepository userRepository;
    private final ShipmentStateMachine stateMachine;
    private final ApplicationEventPublisher eventPublisher;

    public Shipment getShipmentById(Long id) {
        return shipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vận đơn với ID: " + id));
    }

    public void assertCanViewShipment(Shipment shipment, User actor) {
        if (shipment == null || actor == null || actor.getRole() == null) {
            throw new AccessDeniedException("Bạn không có quyền truy cập vận đơn này");
        }

        switch (actor.getRole()) {
            case ADMIN -> {
                return;
            }
            case CUSTOMER -> {
                if (shipment.getCustomer() != null && Objects.equals(shipment.getCustomer().getId(), actor.getId())) {
                    return;
                }
            }
            case STAFF -> {
                if (isStaffInHubScope(shipment, actor)) {
                    return;
                }
            }
            case SHIPPER -> {
                boolean isPickup = shipment.getPickupShipper() != null
                        && Objects.equals(shipment.getPickupShipper().getId(), actor.getId());
                boolean isDelivery = shipment.getDeliveryShipper() != null
                        && Objects.equals(shipment.getDeliveryShipper().getId(), actor.getId());
                if (isPickup || isDelivery) {
                    return;
                }
            }
        }

        throw new AccessDeniedException("Bạn không có quyền truy cập vận đơn này");
    }

    public void assertStaffOwnsHub(User actor, Long requiredHubId) {
        if (actor == null || actor.getRole() == null) {
            throw new AccessDeniedException("Nhân viên chỉ được thao tác vận đơn thuộc bưu cục của mình");
        }
        if (actor.getRole() == Role.ADMIN) {
            return;
        }
        if (actor.getRole() == Role.STAFF
                && actor.getHub() != null
                && requiredHubId != null
                && Objects.equals(actor.getHub().getId(), requiredHubId)) {
            return;
        }
        throw new AccessDeniedException("Nhân viên chỉ được thao tác vận đơn thuộc bưu cục của mình");
    }

    public void assertAssignedShipper(User assignedShipper, User actor, String actionLabel) {
        if (actor == null
                || actor.getId() == null
                || (actor.getRole() != null && actor.getRole() != Role.SHIPPER)
                || assignedShipper == null
                || !Objects.equals(assignedShipper.getId(), actor.getId())) {
            throw new AccessDeniedException("Bạn không được phân công " + actionLabel + " cho vận đơn này");
        }
    }

    @Transactional
    public Shipment assignPickupShipper(Long shipmentId, Long shipperId, User actor) {
        Shipment shipment = getShipmentById(shipmentId);
        Hub originHub = shipment.getOriginHub();
        assertStaffOwnsHub(actor, originHub != null ? originHub.getId() : null);
        stateMachine.validateTransition(shipment, ShipmentStatus.ASSIGNED_PICKUP);

        User shipper = validateHubShipper(shipperId, originHub, "lấy hàng", "bưu cục gửi");

        shipment.setPickupShipper(shipper);
        shipment.setStatus(ShipmentStatus.ASSIGNED_PICKUP);
        Shipment saved = shipmentRepository.save(shipment);

        String description = String.format(
                "%s đã phân công Shipper %s%s đến lấy hàng.",
                originHub.getName(),
                shipper.getFullName(),
                formatPhoneSuffix(shipper.getPhone())
        );
        recordTrackingLog(
                saved,
                ShipmentStatus.ASSIGNED_PICKUP,
                originHub,
                actor,
                "Đã phân công Shipper lấy hàng",
                description
        );
        return saved;
    }

    @Transactional
    public Shipment markPickedUp(Long shipmentId, User shipper, String note) {
        Shipment shipment = getShipmentById(shipmentId);
        assertAssignedShipper(shipment.getPickupShipper(), shipper, "lấy hàng");
        stateMachine.validateTransition(shipment, ShipmentStatus.PICKED_UP);

        shipment.setStatus(ShipmentStatus.PICKED_UP);
        Shipment saved = shipmentRepository.save(shipment);

        String description = appendNote(
                "Shipper " + shipper.getFullName() + " đã nhận kiện hàng từ người gửi.",
                note
        );
        recordTrackingLog(
                saved,
                ShipmentStatus.PICKED_UP,
                saved.getOriginHub(),
                shipper,
                "Shipper đã lấy hàng thành công",
                description
        );
        return saved;
    }

    @Transactional
    public Shipment arriveAtOriginHub(Long shipmentId, User actor, String note) {
        Shipment shipment = getShipmentById(shipmentId);
        Hub originHub = shipment.getOriginHub();
        assertStaffOwnsHub(actor, originHub != null ? originHub.getId() : null);
        stateMachine.validateTransition(shipment, ShipmentStatus.IN_ORIGIN_HUB);

        shipment.setStatus(ShipmentStatus.IN_ORIGIN_HUB);
        shipment.setCurrentHub(originHub);
        Shipment saved = shipmentRepository.save(shipment);

        String title = String.format("Đã nhập kho %s (%s)", originHub.getName(), originHub.getCode());
        String description = appendNote("Kiện hàng đã được kiểm đếm và nhập kho bưu cục gửi.", note);
        recordTrackingLog(saved, ShipmentStatus.IN_ORIGIN_HUB, originHub, actor, title, description);
        return saved;
    }

    @Transactional
    public Shipment dispatchTransit(Long shipmentId, User actor, String note) {
        Shipment shipment = getShipmentById(shipmentId);
        Hub originHub = shipment.getOriginHub();
        Hub destHub = shipment.getDestHub();
        assertStaffOwnsHub(actor, originHub != null ? originHub.getId() : null);
        stateMachine.validateTransition(shipment, ShipmentStatus.IN_TRANSIT);

        shipment.setStatus(ShipmentStatus.IN_TRANSIT);
        Shipment saved = shipmentRepository.save(shipment);

        String baseDescription = String.format(
                "Đang luân chuyển liên tỉnh từ %s (%s) đến %s (%s).",
                originHub.getName(), originHub.getCode(),
                destHub.getName(), destHub.getCode()
        );
        recordTrackingLog(
                saved,
                ShipmentStatus.IN_TRANSIT,
                originHub,
                actor,
                "Đã xuất kho trung chuyển liên tỉnh",
                appendNote(baseDescription, note)
        );
        return saved;
    }

    @Transactional
    public Shipment arriveAtDestHub(Long shipmentId, User actor, String note) {
        Shipment shipment = getShipmentById(shipmentId);
        Hub destHub = shipment.getDestHub();
        assertStaffOwnsHub(actor, destHub != null ? destHub.getId() : null);
        boolean isIntraHubDirect = shipment.getStatus() == ShipmentStatus.IN_ORIGIN_HUB;
        stateMachine.validateTransition(shipment, ShipmentStatus.IN_DEST_HUB);

        shipment.setStatus(ShipmentStatus.IN_DEST_HUB);
        shipment.setCurrentHub(destHub);
        Shipment saved = shipmentRepository.save(shipment);

        String title = isIntraHubDirect
                ? String.format("Hàng sẵn sàng phát tại %s (%s)", destHub.getName(), destHub.getCode())
                : String.format("Đã nhập kho %s (%s)", destHub.getName(), destHub.getCode());
        String baseDescription = isIntraHubDirect
                ? "Đơn nội vùng chuyển thẳng sang khâu sẵn sàng phân công giao hàng."
                : "Kiện hàng đã đến bưu cục nhận và sẵn sàng phân công giao hàng.";
        recordTrackingLog(saved, ShipmentStatus.IN_DEST_HUB, destHub, actor, title, appendNote(baseDescription, note));
        return saved;
    }

    @Transactional
    public Shipment assignDeliveryShipper(Long shipmentId, Long shipperId, User actor) {
        Shipment shipment = getShipmentById(shipmentId);
        Hub destHub = shipment.getDestHub();
        assertStaffOwnsHub(actor, destHub != null ? destHub.getId() : null);
        boolean isRedelivery = shipment.getStatus() == ShipmentStatus.DELIVERY_FAILED;
        stateMachine.validateTransition(shipment, ShipmentStatus.OUT_FOR_DELIVERY);

        User shipper = validateHubShipper(shipperId, destHub, "giao hàng", "bưu cục nhận");

        shipment.setDeliveryShipper(shipper);
        shipment.setStatus(ShipmentStatus.OUT_FOR_DELIVERY);
        Shipment saved = shipmentRepository.save(shipment);

        String title = isRedelivery ? "Sắp xếp giao lại hàng" : "Shipper đang trên đường giao hàng";
        String description = String.format(
                "%s đã phân công Shipper %s%s đi giao kiện hàng tới người nhận.",
                destHub.getName(),
                shipper.getFullName(),
                formatPhoneSuffix(shipper.getPhone())
        );
        recordTrackingLog(saved, ShipmentStatus.OUT_FOR_DELIVERY, destHub, actor, title, description);
        return saved;
    }

    @Transactional
    public Shipment markDelivered(Long shipmentId, User shipper, String podImageKey, String note) {
        Shipment shipment = getShipmentById(shipmentId);
        assertAssignedShipper(shipment.getDeliveryShipper(), shipper, "giao hàng");
        stateMachine.validateTransition(shipment, ShipmentStatus.DELIVERED);

        if (podImageKey == null || podImageKey.isBlank()) {
            throw new BusinessException("Bắt buộc phải đính kèm ảnh chứng từ giao hàng (POD) khi xác nhận giao thành công");
        }

        shipment.setStatus(ShipmentStatus.DELIVERED);
        shipment.setPodImageKey(podImageKey.trim());
        shipment.setDeliveredAt(OffsetDateTime.now());

        boolean hasCod = shipment.getCodAmount() != null && shipment.getCodAmount().compareTo(BigDecimal.ZERO) > 0;
        boolean receiverPaysUnpaid = shipment.getPayerType() == PayerType.RECEIVER
                && shipment.getPaymentStatus() == PaymentStatus.UNPAID;
        shipment.setCodStatus((hasCod || receiverPaysUnpaid) ? CodStatus.HELD_BY_SHIPPER : CodStatus.NONE);

        Shipment saved = shipmentRepository.save(shipment);

        String description = appendNote(
                "Shipper " + shipper.getFullName() + " đã giao hàng thành công và cập nhật ảnh POD.",
                note
        );
        recordTrackingLog(
                saved,
                ShipmentStatus.DELIVERED,
                saved.getDestHub(),
                shipper,
                "Giao hàng thành công",
                description
        );
        return saved;
    }

    @Transactional
    public Shipment markDeliveryFailed(Long shipmentId, User shipper, String reason) {
        Shipment shipment = getShipmentById(shipmentId);
        assertAssignedShipper(shipment.getDeliveryShipper(), shipper, "giao hàng");
        stateMachine.validateTransition(shipment, ShipmentStatus.DELIVERY_FAILED);

        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Bắt buộc phải nhập lý do giao hàng thất bại");
        }

        int currentFailedCount = shipment.getFailedDeliveryCount() == null ? 0 : shipment.getFailedDeliveryCount();
        int nextFailedCount = currentFailedCount + 1;
        shipment.setFailedDeliveryCount(nextFailedCount);
        shipment.setStatus(ShipmentStatus.DELIVERY_FAILED);

        Shipment saved = shipmentRepository.save(shipment);

        recordTrackingLog(
                saved,
                ShipmentStatus.DELIVERY_FAILED,
                saved.getDestHub(),
                shipper,
                "Giao hàng thất bại (Lần " + nextFailedCount + ")",
                "Lý do: " + reason.trim()
        );
        return saved;
    }

    @Transactional
    public Shipment startReturn(Long shipmentId, User actor, String reason) {
        Shipment shipment = getShipmentById(shipmentId);
        Hub destHub = shipment.getDestHub();
        assertStaffOwnsHub(actor, destHub != null ? destHub.getId() : null);
        stateMachine.validateTransition(shipment, ShipmentStatus.RETURNING);

        shipment.setStatus(ShipmentStatus.RETURNING);
        Shipment saved = shipmentRepository.save(shipment);

        String description = (reason != null && !reason.isBlank())
                ? "Lý do hoàn hàng: " + reason.trim()
                : "Bưu cục nhận đã kích hoạt quy trình hoàn hàng về người gửi.";
        recordTrackingLog(
                saved,
                ShipmentStatus.RETURNING,
                destHub,
                actor,
                "Kích hoạt quy trình hoàn hàng",
                description
        );
        return saved;
    }

    @Transactional
    public Shipment completeReturn(Long shipmentId, User actor, String note) {
        Shipment shipment = getShipmentById(shipmentId);
        assertStaffOwnsOriginOrDestHub(shipment, actor);
        stateMachine.validateTransition(shipment, ShipmentStatus.RETURNED);

        Hub originHub = shipment.getOriginHub();
        shipment.setStatus(ShipmentStatus.RETURNED);
        shipment.setCurrentHub(originHub);
        Shipment saved = shipmentRepository.save(shipment);

        String description = appendNote(
                "Kiện hàng đã được hoàn trả thành công về người gửi tại " + originHub.getName() + ".",
                note
        );
        recordTrackingLog(
                saved,
                ShipmentStatus.RETURNED,
                originHub,
                actor,
                "Đã hoàn hàng về người gửi",
                description
        );
        return saved;
    }

    @Transactional
    public Shipment cancelShipment(Long shipmentId, User actor, String reason) {
        Shipment shipment = getShipmentById(shipmentId);
        assertCanCancelShipment(shipment, actor);

        if (!stateMachine.isCancelable(shipment.getStatus())) {
            throw new BusinessException("Không thể hủy vận đơn sau khi shipper đã lấy hàng");
        }
        stateMachine.validateTransition(shipment, ShipmentStatus.CANCELLED);

        if (shipment.getPaymentStatus() == PaymentStatus.PAID) {
            shipment.setPaymentStatus(PaymentStatus.REFUND_PENDING);
        }
        shipment.setStatus(ShipmentStatus.CANCELLED);
        Shipment saved = shipmentRepository.save(shipment);

        String baseDescription = (reason != null && !reason.isBlank())
                ? "Lý do hủy: " + reason.trim()
                : "Vận đơn đã được hủy theo yêu cầu.";
        if (saved.getPaymentStatus() == PaymentStatus.REFUND_PENDING) {
            baseDescription += " Đơn đã thanh toán cước trực tuyến, chuyển trạng thái chờ hoàn tiền.";
        }
        Hub logHub = saved.getCurrentHub() != null ? saved.getCurrentHub() : saved.getOriginHub();
        recordTrackingLog(
                saved,
                ShipmentStatus.CANCELLED,
                logHub,
                actor,
                "Vận đơn đã bị hủy",
                baseDescription
        );
        return saved;
    }

    public ShipmentTrackingLog recordTrackingLog(Shipment shipment,
                                                 ShipmentStatus status,
                                                 Hub hub,
                                                 User actor,
                                                 String title,
                                                 String description) {
        ShipmentTrackingLog log = ShipmentTrackingLog.builder()
                .shipment(shipment)
                .status(status)
                .hub(hub)
                .actor(actor)
                .title(title)
                .description(description)
                .createdAt(OffsetDateTime.now())
                .build();
        ShipmentTrackingLog savedLog = trackingLogRepository.save(log);
        if (eventPublisher != null) {
            eventPublisher.publishEvent(ShipmentStatusChangedEvent.from(shipment, savedLog));
        }
        return savedLog;
    }

    public BigDecimal calculateReceiverCollectAmount(Shipment shipment) {
        if (shipment == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal cod = shipment.getCodAmount() != null ? shipment.getCodAmount() : BigDecimal.ZERO;
        BigDecimal feeToCollect = BigDecimal.ZERO;
        if (shipment.getPayerType() == PayerType.RECEIVER
                && shipment.getPaymentStatus() != PaymentStatus.PAID
                && shipment.getTotalFee() != null) {
            feeToCollect = shipment.getTotalFee();
        }
        return cod.add(feeToCollect);
    }

    private User validateHubShipper(Long shipperId, Hub requiredHub, String taskLabel, String hubLabel) {
        if (shipperId == null) {
            throw new BusinessException("Vui lòng chọn Shipper " + taskLabel);
        }
        User shipper = userRepository.findById(shipperId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Shipper với ID: " + shipperId));

        if (shipper.getRole() != Role.SHIPPER) {
            throw new BusinessException("Tài khoản được chọn không phải vai trò Shipper");
        }
        if (shipper.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("Tài khoản Shipper đang bị khóa, không thể phân công");
        }
        if (shipper.getHub() == null
                || requiredHub == null
                || !Objects.equals(shipper.getHub().getId(), requiredHub.getId())) {
            throw new BusinessException("Shipper được chọn không trực thuộc " + hubLabel);
        }
        return shipper;
    }

    private boolean isStaffInHubScope(Shipment shipment, User actor) {
        if (actor.getHub() == null || actor.getHub().getId() == null) {
            return false;
        }
        Long actorHubId = actor.getHub().getId();
        boolean matchesOrigin = shipment.getOriginHub() != null && Objects.equals(shipment.getOriginHub().getId(), actorHubId);
        boolean matchesDest = shipment.getDestHub() != null && Objects.equals(shipment.getDestHub().getId(), actorHubId);
        boolean matchesCurrent = shipment.getCurrentHub() != null && Objects.equals(shipment.getCurrentHub().getId(), actorHubId);
        return matchesOrigin || matchesDest || matchesCurrent;
    }

    private void assertStaffOwnsOriginOrDestHub(Shipment shipment, User actor) {
        if (actor == null || actor.getRole() == null) {
            throw new AccessDeniedException("Nhân viên chỉ được thao tác vận đơn thuộc bưu cục của mình");
        }
        if (actor.getRole() == Role.ADMIN) {
            return;
        }
        if (actor.getRole() == Role.STAFF && actor.getHub() != null && actor.getHub().getId() != null) {
            Long actorHubId = actor.getHub().getId();
            boolean matchesOrigin = shipment.getOriginHub() != null && Objects.equals(shipment.getOriginHub().getId(), actorHubId);
            boolean matchesDest = shipment.getDestHub() != null && Objects.equals(shipment.getDestHub().getId(), actorHubId);
            if (matchesOrigin || matchesDest) {
                return;
            }
        }
        throw new AccessDeniedException("Nhân viên chỉ được thao tác vận đơn thuộc bưu cục của mình");
    }

    private void assertCanCancelShipment(Shipment shipment, User actor) {
        if (actor == null || actor.getRole() == null) {
            throw new AccessDeniedException("Bạn không có quyền hủy vận đơn này");
        }
        switch (actor.getRole()) {
            case ADMIN -> {
                return;
            }
            case CUSTOMER -> {
                if (shipment.getCustomer() != null && Objects.equals(shipment.getCustomer().getId(), actor.getId())) {
                    return;
                }
                throw new AccessDeniedException("Bạn không có quyền hủy vận đơn này");
            }
            case STAFF -> {
                if (isStaffInHubScope(shipment, actor)) {
                    return;
                }
                throw new AccessDeniedException("Nhân viên chỉ được thao tác vận đơn thuộc bưu cục của mình");
            }
            case SHIPPER -> throw new AccessDeniedException("Shipper không có quyền hủy vận đơn");
        }
    }

    private String appendNote(String base, String note) {
        if (note == null || note.isBlank()) {
            return base;
        }
        return base + " Ghi chú: " + note.trim();
    }

    private String formatPhoneSuffix(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        return " (" + phone.trim() + ")";
    }
}
