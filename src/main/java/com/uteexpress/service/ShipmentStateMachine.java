package com.uteexpress.service;

import com.uteexpress.entity.Shipment;
import com.uteexpress.enums.ShipmentStatus;
import com.uteexpress.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class ShipmentStateMachine {

    public static final int MAX_FAILED_DELIVERY_ATTEMPTS = 3;

    private static final Map<ShipmentStatus, Set<ShipmentStatus>> ALLOWED_TRANSITIONS;

    static {
        Map<ShipmentStatus, Set<ShipmentStatus>> transitions = new EnumMap<>(ShipmentStatus.class);
        transitions.put(ShipmentStatus.PENDING_PAYMENT, Set.of(ShipmentStatus.CREATED, ShipmentStatus.CANCELLED));
        transitions.put(ShipmentStatus.CREATED, Set.of(ShipmentStatus.ASSIGNED_PICKUP, ShipmentStatus.CANCELLED));
        transitions.put(ShipmentStatus.ASSIGNED_PICKUP, Set.of(ShipmentStatus.PICKED_UP, ShipmentStatus.CANCELLED));
        transitions.put(ShipmentStatus.PICKED_UP, Set.of(ShipmentStatus.IN_ORIGIN_HUB));
        transitions.put(ShipmentStatus.IN_ORIGIN_HUB, Set.of(ShipmentStatus.IN_TRANSIT, ShipmentStatus.IN_DEST_HUB));
        transitions.put(ShipmentStatus.IN_TRANSIT, Set.of(ShipmentStatus.IN_DEST_HUB));
        transitions.put(ShipmentStatus.IN_DEST_HUB, Set.of(ShipmentStatus.OUT_FOR_DELIVERY));
        transitions.put(ShipmentStatus.OUT_FOR_DELIVERY, Set.of(ShipmentStatus.DELIVERED, ShipmentStatus.DELIVERY_FAILED));
        transitions.put(ShipmentStatus.DELIVERY_FAILED, Set.of(ShipmentStatus.OUT_FOR_DELIVERY, ShipmentStatus.RETURNING));
        transitions.put(ShipmentStatus.RETURNING, Set.of(ShipmentStatus.RETURNED));
        transitions.put(ShipmentStatus.DELIVERED, Set.of());
        transitions.put(ShipmentStatus.RETURNED, Set.of());
        transitions.put(ShipmentStatus.CANCELLED, Set.of());
        ALLOWED_TRANSITIONS = Map.copyOf(transitions);
    }

    public boolean canTransition(ShipmentStatus current, ShipmentStatus target) {
        if (current == null || target == null) {
            return false;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(target);
    }

    public void validateTransition(Shipment shipment, ShipmentStatus target) {
        if (shipment == null || shipment.getStatus() == null || target == null) {
            throw new BusinessException("Trạng thái vận đơn không hợp lệ");
        }

        ShipmentStatus current = shipment.getStatus();
        if (!canTransition(current, target)) {
            throw new BusinessException(String.format(
                    "Không thể chuyển trạng thái vận đơn từ %s (%s) sang %s (%s)",
                    current.getLabel(), current.name(), target.getLabel(), target.name()
            ));
        }

        if (current == ShipmentStatus.IN_ORIGIN_HUB) {
            if (shipment.getOriginHub() == null || shipment.getDestHub() == null) {
                throw new BusinessException("Thông tin bưu cục gửi hoặc bưu cục nhận không hợp lệ");
            }
            boolean isSameHub = Objects.equals(shipment.getOriginHub().getId(), shipment.getDestHub().getId());

            if (target == ShipmentStatus.IN_TRANSIT && isSameHub) {
                throw new BusinessException("Đơn nội vùng không chuyển qua trung chuyển liên tỉnh, hãy chuyển thẳng sẵn sàng giao");
            }
            if (target == ShipmentStatus.IN_DEST_HUB && !isSameHub) {
                throw new BusinessException("Đơn liên tỉnh bắt buộc xuất kho trung chuyển trước khi nhập bưu cục nhận");
            }
        }

        if (current == ShipmentStatus.DELIVERY_FAILED && target == ShipmentStatus.OUT_FOR_DELIVERY) {
            int failedCount = shipment.getFailedDeliveryCount() == null ? 0 : shipment.getFailedDeliveryCount();
            if (failedCount >= MAX_FAILED_DELIVERY_ATTEMPTS) {
                throw new BusinessException("Vận đơn đã giao thất bại đủ 3 lần, không thể giao lại mà phải kích hoạt hoàn hàng");
            }
        }
    }

    public boolean isCancelable(ShipmentStatus status) {
        return status == ShipmentStatus.PENDING_PAYMENT
                || status == ShipmentStatus.CREATED
                || status == ShipmentStatus.ASSIGNED_PICKUP;
    }

    public Set<ShipmentStatus> getAllowedTransitions(ShipmentStatus current) {
        if (current == null) {
            return Set.of();
        }
        return ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
    }
}
