package com.uteexpress.service;

import com.uteexpress.dto.staff.StaffDashboardSummary;
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
import com.uteexpress.repository.ShipmentRepository;
import com.uteexpress.repository.ShipmentTrackingLogRepository;
import com.uteexpress.repository.UserRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StaffShipmentQueryService {

    public static final String TAB_ALL = "all";
    public static final String TAB_PENDING_PICKUP = "pending_pickup";
    public static final String TAB_IN_ORIGIN = "in_origin";
    public static final String TAB_INCOMING_TRANSIT = "incoming_transit";
    public static final String TAB_READY_DELIVERY = "ready_delivery";
    public static final String TAB_FAILED_RETURN = "failed_return";
    public static final String TAB_COMPLETED = "completed";

    private static final List<ShipmentStatus> SHIPPER_COMPLETED_STATUSES = List.of(
            ShipmentStatus.PICKED_UP,
            ShipmentStatus.IN_ORIGIN_HUB,
            ShipmentStatus.IN_TRANSIT,
            ShipmentStatus.IN_DEST_HUB,
            ShipmentStatus.DELIVERED,
            ShipmentStatus.DELIVERY_FAILED,
            ShipmentStatus.RETURNING,
            ShipmentStatus.RETURNED
    );

    private final ShipmentRepository shipmentRepository;
    private final ShipmentTrackingLogRepository trackingLogRepository;
    private final UserRepository userRepository;

    public String normalizeTab(String tab) {
        if (tab == null || tab.isBlank()) {
            return TAB_ALL;
        }
        return switch (tab.trim().toLowerCase(Locale.ROOT)) {
            case TAB_PENDING_PICKUP -> TAB_PENDING_PICKUP;
            case TAB_IN_ORIGIN -> TAB_IN_ORIGIN;
            case TAB_INCOMING_TRANSIT -> TAB_INCOMING_TRANSIT;
            case TAB_READY_DELIVERY -> TAB_READY_DELIVERY;
            case TAB_FAILED_RETURN -> TAB_FAILED_RETURN;
            case TAB_COMPLETED -> TAB_COMPLETED;
            default -> TAB_ALL;
        };
    }

    public String resolveHubLabel(User actor) {
        if (actor == null || actor.getRole() == null) {
            return "Không xác định";
        }
        if (actor.getRole() == Role.ADMIN) {
            return "Toàn chuỗi UTEExpress (Admin)";
        }
        Hub hub = actor.getHub();
        if (hub != null) {
            return hub.getCode() + " — " + hub.getName();
        }
        return "Chưa gán bưu cục";
    }

    @Transactional(readOnly = true)
    public StaffDashboardSummary getStaffDashboard(User actor) {
        Long scopedHubId = resolveScopedHubId(actor);
        String hubLabel = resolveHubLabel(actor);

        long waitingPickupCount = shipmentRepository.count(buildTabSpecification(scopedHubId, TAB_PENDING_PICKUP, null));
        long inOriginHubCount = shipmentRepository.count(buildTabSpecification(scopedHubId, TAB_IN_ORIGIN, null));
        long incomingTransitCount = shipmentRepository.count(buildTabSpecification(scopedHubId, TAB_INCOMING_TRANSIT, null));
        long readyForDeliveryCount = shipmentRepository.count(buildTabSpecification(scopedHubId, TAB_READY_DELIVERY, null));
        long failedOrReturningCount = shipmentRepository.count(buildTabSpecification(scopedHubId, TAB_FAILED_RETURN, null));

        List<Shipment> heldCodShipments;
        if (scopedHubId != null) {
            heldCodShipments = shipmentRepository.findByDestHubIdAndCodStatusOrderByDeliveredAtDesc(
                    scopedHubId, CodStatus.HELD_BY_SHIPPER);
        } else {
            Specification<Shipment> heldCodSpec = (root, query, cb) ->
                    cb.equal(root.get("codStatus"), CodStatus.HELD_BY_SHIPPER);
            heldCodShipments = shipmentRepository.findAll(heldCodSpec, Sort.by(Sort.Direction.DESC, "deliveredAt"));
        }

        long heldCodShipmentCount = heldCodShipments.size();
        BigDecimal heldCodAmount = heldCodShipments.stream()
                .map(this::calculateHeldCashAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Shipment> urgentShipments = shipmentRepository.findAll(
                buildUrgentSpecification(scopedHubId),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "updatedAt"))
        ).getContent();

        return new StaffDashboardSummary(
                hubLabel,
                waitingPickupCount,
                inOriginHubCount,
                incomingTransitCount,
                readyForDeliveryCount,
                failedOrReturningCount,
                heldCodShipmentCount,
                heldCodAmount,
                urgentShipments
        );
    }

    @Transactional(readOnly = true)
    public Page<Shipment> searchStaffShipments(User actor, String tab, String keyword, Pageable pageable) {
        Long scopedHubId = resolveScopedHubId(actor);
        Pageable effectivePageable = (pageable != null)
                ? pageable
                : PageRequest.of(0, 12, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return shipmentRepository.findAll(buildTabSpecification(scopedHubId, tab, keyword), effectivePageable);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getStaffTabCounts(User actor) {
        Long scopedHubId = resolveScopedHubId(actor);
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put(TAB_ALL, shipmentRepository.count(buildTabSpecification(scopedHubId, TAB_ALL, null)));
        counts.put(TAB_PENDING_PICKUP, shipmentRepository.count(buildTabSpecification(scopedHubId, TAB_PENDING_PICKUP, null)));
        counts.put(TAB_IN_ORIGIN, shipmentRepository.count(buildTabSpecification(scopedHubId, TAB_IN_ORIGIN, null)));
        counts.put(TAB_INCOMING_TRANSIT, shipmentRepository.count(buildTabSpecification(scopedHubId, TAB_INCOMING_TRANSIT, null)));
        counts.put(TAB_READY_DELIVERY, shipmentRepository.count(buildTabSpecification(scopedHubId, TAB_READY_DELIVERY, null)));
        counts.put(TAB_FAILED_RETURN, shipmentRepository.count(buildTabSpecification(scopedHubId, TAB_FAILED_RETURN, null)));
        counts.put(TAB_COMPLETED, shipmentRepository.count(buildTabSpecification(scopedHubId, TAB_COMPLETED, null)));
        return counts;
    }

    @Transactional(readOnly = true)
    public List<User> getActiveShippersForHub(Long hubId) {
        if (hubId == null) {
            return List.of();
        }
        return userRepository.findByHubIdAndRoleAndStatusOrderByFullNameAsc(hubId, Role.SHIPPER, UserStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<ShipmentTrackingLog> getTrackingLogs(Long shipmentId) {
        if (shipmentId == null) {
            return List.of();
        }
        return trackingLogRepository.findByShipmentIdOrderByCreatedAtDescIdDesc(shipmentId);
    }

    // ==================== SHIPPER TASKS QUERIES ====================

    @Transactional(readOnly = true)
    public List<Shipment> getShipperPickupTasks(Long shipperId) {
        if (shipperId == null) {
            return List.of();
        }
        return shipmentRepository.findByPickupShipperIdAndStatusOrderByCreatedAtDesc(
                shipperId, ShipmentStatus.ASSIGNED_PICKUP);
    }

    @Transactional(readOnly = true)
    public List<Shipment> getShipperDeliveryTasks(Long shipperId) {
        if (shipperId == null) {
            return List.of();
        }
        return shipmentRepository.findByDeliveryShipperIdAndStatusOrderByCreatedAtDesc(
                shipperId, ShipmentStatus.OUT_FOR_DELIVERY);
    }

    @Transactional(readOnly = true)
    public List<Shipment> getShipperCompletedTasks(Long shipperId) {
        if (shipperId == null) {
            return List.of();
        }
        return shipmentRepository.findShipperCompletedTasks(shipperId, SHIPPER_COMPLETED_STATUSES);
    }

    // ==================== INTERNAL HELPERS (BR-10 HUB SCOPING) ====================

    private Long resolveScopedHubId(User actor) {
        if (actor == null || actor.getRole() == null) {
            throw new AccessDeniedException("Bạn không có quyền truy cập cổng vận hành bưu cục");
        }
        if (actor.getRole() == Role.ADMIN) {
            return null;
        }
        if (actor.getRole() == Role.STAFF && actor.getHub() != null && actor.getHub().getId() != null) {
            return actor.getHub().getId();
        }
        throw new AccessDeniedException("Tài khoản nhân viên chưa được gán bưu cục trực thuộc hợp lệ");
    }

    private Specification<Shipment> buildTabSpecification(Long hubId, String tab, String keyword) {
        String normalizedTab = normalizeTab(tab);
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            switch (normalizedTab) {
                case TAB_PENDING_PICKUP -> {
                    if (hubId != null) {
                        predicates.add(cb.equal(root.get("originHub").get("id"), hubId));
                    }
                    predicates.add(root.get("status").in(ShipmentStatus.CREATED, ShipmentStatus.ASSIGNED_PICKUP));
                }
                case TAB_IN_ORIGIN -> {
                    if (hubId != null) {
                        predicates.add(cb.equal(root.get("originHub").get("id"), hubId));
                    }
                    predicates.add(root.get("status").in(ShipmentStatus.PICKED_UP, ShipmentStatus.IN_ORIGIN_HUB));
                }
                case TAB_INCOMING_TRANSIT -> {
                    if (hubId != null) {
                        predicates.add(cb.equal(root.get("destHub").get("id"), hubId));
                    }
                    predicates.add(cb.equal(root.get("status"), ShipmentStatus.IN_TRANSIT));
                }
                case TAB_READY_DELIVERY -> {
                    if (hubId != null) {
                        predicates.add(cb.equal(root.get("destHub").get("id"), hubId));
                    }
                    predicates.add(root.get("status").in(ShipmentStatus.IN_DEST_HUB, ShipmentStatus.OUT_FOR_DELIVERY));
                }
                case TAB_FAILED_RETURN -> {
                    if (hubId != null) {
                        predicates.add(cb.or(
                                cb.equal(root.get("destHub").get("id"), hubId),
                                cb.equal(root.get("originHub").get("id"), hubId)
                        ));
                    }
                    predicates.add(root.get("status").in(ShipmentStatus.DELIVERY_FAILED, ShipmentStatus.RETURNING));
                }
                case TAB_COMPLETED -> {
                    if (hubId != null) {
                        predicates.add(touchesHub(root, cb, hubId));
                    }
                    predicates.add(root.get("status").in(
                            ShipmentStatus.DELIVERED, ShipmentStatus.RETURNED, ShipmentStatus.CANCELLED));
                }
                default -> {
                    if (hubId != null) {
                        predicates.add(touchesHub(root, cb, hubId));
                    }
                }
            }

            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("trackingCode")), pattern),
                        cb.like(cb.lower(root.get("senderName")), pattern),
                        cb.like(cb.lower(root.get("senderPhone")), pattern),
                        cb.like(cb.lower(root.get("receiverName")), pattern),
                        cb.like(cb.lower(root.get("receiverPhone")), pattern)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<Shipment> buildUrgentSpecification(Long hubId) {
        return (root, query, cb) -> {
            if (hubId == null) {
                return root.get("status").in(
                        ShipmentStatus.CREATED,
                        ShipmentStatus.PICKED_UP,
                        ShipmentStatus.IN_ORIGIN_HUB,
                        ShipmentStatus.IN_TRANSIT,
                        ShipmentStatus.IN_DEST_HUB,
                        ShipmentStatus.DELIVERY_FAILED,
                        ShipmentStatus.RETURNING
                );
            }
            Predicate originAction = cb.and(
                    cb.equal(root.get("originHub").get("id"), hubId),
                    root.get("status").in(
                            ShipmentStatus.CREATED,
                            ShipmentStatus.PICKED_UP,
                            ShipmentStatus.IN_ORIGIN_HUB
                    )
            );
            Predicate destAction = cb.and(
                    cb.equal(root.get("destHub").get("id"), hubId),
                    root.get("status").in(
                            ShipmentStatus.IN_TRANSIT,
                            ShipmentStatus.IN_DEST_HUB,
                            ShipmentStatus.DELIVERY_FAILED
                    )
            );
            Predicate returnAction = cb.and(
                    cb.or(
                            cb.equal(root.get("originHub").get("id"), hubId),
                            cb.equal(root.get("destHub").get("id"), hubId)
                    ),
                    cb.equal(root.get("status"), ShipmentStatus.RETURNING)
            );
            return cb.or(originAction, destAction, returnAction);
        };
    }

    private Predicate touchesHub(Root<Shipment> root, CriteriaBuilder cb, Long hubId) {
        return cb.or(
                cb.equal(root.get("originHub").get("id"), hubId),
                cb.equal(root.get("destHub").get("id"), hubId),
                cb.equal(root.get("currentHub").get("id"), hubId)
        );
    }

    private BigDecimal calculateHeldCashAmount(Shipment shipment) {
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
}
