package com.uteexpress.service;

import com.uteexpress.dto.publicview.PublicTrackingItemDto;
import com.uteexpress.dto.publicview.PublicTrackingLogDto;
import com.uteexpress.dto.publicview.PublicTrackingResponse;
import com.uteexpress.entity.Hub;
import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.ShipmentTrackingLog;
import com.uteexpress.enums.ShipmentStatus;
import com.uteexpress.repository.ShipmentRepository;
import com.uteexpress.repository.ShipmentTrackingLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PublicTrackingService {

    public static final int MAX_TRACKING_CODES = 5;
    public static final String LIMIT_EXCEEDED_WARNING =
            "Hệ thống hỗ trợ tra cứu tối đa 5 mã vận đơn mỗi lần. Đã hiển thị 5 mã đầu tiên.";

    private final ShipmentRepository shipmentRepository;
    private final ShipmentTrackingLogRepository shipmentTrackingLogRepository;

    public String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String trimmed = phone.trim();
        if (trimmed.length() <= 3) {
            return "****";
        }
        if (trimmed.length() <= 6) {
            return trimmed.substring(0, 2) + "****";
        }
        return trimmed.substring(0, 3) + "****" + trimmed.substring(trimmed.length() - 3);
    }

    public String maskName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1) + "***";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(parts[i]);
        }
        sb.append(" ***");
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public PublicTrackingResponse lookupByCodes(String rawCodes) {
        if (rawCodes == null || rawCodes.isBlank()) {
            return new PublicTrackingResponse(List.of(), List.of(), null);
        }

        Set<String> uniqueCodes = new LinkedHashSet<>();
        for (String token : rawCodes.split("[,\\s]+")) {
            String normalized = token.trim().toUpperCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                uniqueCodes.add(normalized);
            }
        }

        if (uniqueCodes.isEmpty()) {
            return new PublicTrackingResponse(List.of(), List.of(), null);
        }

        List<String> allCodes = new ArrayList<>(uniqueCodes);
        String warningMessage = null;
        List<String> targetCodes = allCodes;
        if (allCodes.size() > MAX_TRACKING_CODES) {
            targetCodes = new ArrayList<>(allCodes.subList(0, MAX_TRACKING_CODES));
            warningMessage = LIMIT_EXCEEDED_WARNING;
        }

        List<Shipment> foundShipments = shipmentRepository.findByTrackingCodeIn(targetCodes);
        Map<String, Shipment> byCode = new HashMap<>();
        for (Shipment shipment : foundShipments) {
            if (shipment.getTrackingCode() != null) {
                byCode.put(shipment.getTrackingCode().toUpperCase(Locale.ROOT), shipment);
            }
        }

        List<PublicTrackingItemDto> items = new ArrayList<>();
        List<String> notFoundCodes = new ArrayList<>();

        for (String code : targetCodes) {
            Shipment shipment = byCode.get(code);
            if (shipment == null) {
                notFoundCodes.add(code);
            } else {
                List<ShipmentTrackingLog> logs = shipmentTrackingLogRepository
                        .findByShipmentIdOrderByCreatedAtDescIdDesc(shipment.getId());
                items.add(toPublicItemDto(shipment, logs));
            }
        }

        return new PublicTrackingResponse(items, notFoundCodes, warningMessage);
    }

    public int resolveProgressStep(ShipmentStatus status) {
        if (status == null) {
            return 0;
        }
        return switch (status) {
            case PENDING_PAYMENT, CREATED, ASSIGNED_PICKUP -> 1;
            case PICKED_UP, IN_ORIGIN_HUB -> 2;
            case IN_TRANSIT, IN_DEST_HUB -> 3;
            case OUT_FOR_DELIVERY, DELIVERY_FAILED -> 4;
            case DELIVERED -> 5;
            case RETURNING, RETURNED, CANCELLED -> 0;
        };
    }

    private PublicTrackingItemDto toPublicItemDto(Shipment shipment, List<ShipmentTrackingLog> logs) {
        ShipmentStatus status = shipment.getStatus();
        List<PublicTrackingLogDto> logDtos = logs == null ? List.of() : logs.stream()
                .map(this::toLogDto)
                .toList();

        return new PublicTrackingItemDto(
                shipment.getTrackingCode(),
                status != null ? status.name() : "",
                status != null ? status.getLabel() : "",
                status != null ? status.getBadgeClass() : "bg-secondary-subtle text-secondary",
                resolveProgressStep(status),
                shipment.getServiceType() != null ? shipment.getServiceType().getName() : "",
                shipment.getOriginHub() != null ? shipment.getOriginHub().getCode() : "",
                shipment.getOriginHub() != null ? shipment.getOriginHub().getName() : "",
                shipment.getDestHub() != null ? shipment.getDestHub().getCode() : "",
                shipment.getDestHub() != null ? shipment.getDestHub().getName() : "",
                formatHubDisplay(shipment.getCurrentHub()),
                maskName(shipment.getSenderName()),
                maskPhone(shipment.getSenderPhone()),
                formatRegion(shipment.getSenderDistrict(), shipment.getSenderProvince()),
                maskName(shipment.getReceiverName()),
                maskPhone(shipment.getReceiverPhone()),
                formatRegion(shipment.getReceiverDistrict(), shipment.getReceiverProvince()),
                shipment.getItemName(),
                shipment.getChargeableWeightKg(),
                Boolean.TRUE.equals(shipment.getIsFragile()),
                shipment.getCreatedAt(),
                shipment.getUpdatedAt(),
                logDtos
        );
    }

    private PublicTrackingLogDto toLogDto(ShipmentTrackingLog log) {
        ShipmentStatus status = log.getStatus();
        return new PublicTrackingLogDto(
                status != null ? status.name() : "",
                status != null ? status.getLabel() : "",
                formatHubDisplay(log.getHub()),
                log.getTitle(),
                log.getDescription(),
                log.getCreatedAt()
        );
    }

    private String formatHubDisplay(Hub hub) {
        if (hub == null) {
            return null;
        }
        if (hub.getCode() != null && !hub.getCode().isBlank()) {
            return hub.getName() + " (" + hub.getCode() + ")";
        }
        return hub.getName();
    }

    private String formatRegion(String district, String province) {
        boolean hasDistrict = district != null && !district.isBlank();
        boolean hasProvince = province != null && !province.isBlank();
        if (hasDistrict && hasProvince) {
            return district.trim() + ", " + province.trim();
        }
        if (hasProvince) {
            return province.trim();
        }
        if (hasDistrict) {
            return district.trim();
        }
        return "";
    }
}
