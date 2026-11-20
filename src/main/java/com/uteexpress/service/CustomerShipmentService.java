package com.uteexpress.service;

import com.uteexpress.dto.customer.CreateShipmentRequest;
import com.uteexpress.dto.customer.CustomerDashboardSummary;
import com.uteexpress.dto.pricing.EstimateRequest;
import com.uteexpress.dto.pricing.EstimateResponse;
import com.uteexpress.dto.pricing.ServiceOptionQuote;
import com.uteexpress.entity.Hub;
import com.uteexpress.entity.ServiceType;
import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.ShipmentTrackingLog;
import com.uteexpress.entity.User;
import com.uteexpress.enums.CodStatus;
import com.uteexpress.enums.InspectionPolicy;
import com.uteexpress.enums.PayerType;
import com.uteexpress.enums.PaymentMethod;
import com.uteexpress.enums.PaymentStatus;
import com.uteexpress.enums.ShipmentStatus;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.exception.ResourceNotFoundException;
import com.uteexpress.repository.ServiceTypeRepository;
import com.uteexpress.repository.ShipmentRepository;
import com.uteexpress.repository.ShipmentTrackingLogRepository;
import com.uteexpress.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CustomerShipmentService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TRACKING_DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");
    private static final String TRACKING_CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TRACKING_RANDOM_LENGTH = 6;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static final String TAB_ALL = "all";
    public static final String TAB_PENDING_PAYMENT = "pending_payment";
    public static final String TAB_WAITING_PICKUP = "waiting_pickup";
    public static final String TAB_IN_TRANSIT = "in_transit";
    public static final String TAB_DELIVERING = "delivering";
    public static final String TAB_DELIVERED = "delivered";
    public static final String TAB_FAILED_RETURNED = "failed_returned";
    public static final String TAB_CANCELLED = "cancelled";

    private static final List<ShipmentStatus> WAITING_PICKUP_STATUSES = List.of(
            ShipmentStatus.CREATED,
            ShipmentStatus.ASSIGNED_PICKUP
    );

    private static final List<ShipmentStatus> IN_TRANSIT_STATUSES = List.of(
            ShipmentStatus.PICKED_UP,
            ShipmentStatus.IN_ORIGIN_HUB,
            ShipmentStatus.IN_TRANSIT,
            ShipmentStatus.IN_DEST_HUB
    );

    private static final List<ShipmentStatus> IN_PROGRESS_STATUSES = List.of(
            ShipmentStatus.CREATED,
            ShipmentStatus.ASSIGNED_PICKUP,
            ShipmentStatus.PICKED_UP,
            ShipmentStatus.IN_ORIGIN_HUB,
            ShipmentStatus.IN_TRANSIT,
            ShipmentStatus.IN_DEST_HUB,
            ShipmentStatus.OUT_FOR_DELIVERY
    );

    private static final List<ShipmentStatus> FAILED_RETURNED_STATUSES = List.of(
            ShipmentStatus.DELIVERY_FAILED,
            ShipmentStatus.RETURNING,
            ShipmentStatus.RETURNED
    );

    private static final List<CodStatus> PENDING_COD_SETTLEMENT_STATUSES = List.of(
            CodStatus.HELD_BY_SHIPPER,
            CodStatus.COLLECTED_BY_HUB
    );

    private final ShipmentRepository shipmentRepository;
    private final ShipmentTrackingLogRepository trackingLogRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final UserRepository userRepository;
    private final PricingService pricingService;
    private final ShipmentService shipmentService;

    public String generateTrackingCode() {
        String datePart = LocalDate.now(VIETNAM_ZONE).format(TRACKING_DATE_FORMAT);
        String code;
        do {
            StringBuilder sb = new StringBuilder("UTE").append(datePart);
            for (int i = 0; i < TRACKING_RANDOM_LENGTH; i++) {
                int idx = SECURE_RANDOM.nextInt(TRACKING_CHARSET.length());
                sb.append(TRACKING_CHARSET.charAt(idx));
            }
            code = sb.toString();
        } while (shipmentRepository.existsByTrackingCodeIgnoreCase(code));
        return code;
    }

    @Transactional
    public Shipment createShipment(Long customerId, CreateShipmentRequest req) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản khách hàng"));

        BigDecimal codAmount = req.getCodAmount() != null
                ? req.getCodAmount().setScale(0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        if (codAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Tiền thu hộ COD không được âm");
        }

        Integer lengthCm = normalizePositiveDimension(req.getLengthCm());
        Integer widthCm = normalizePositiveDimension(req.getWidthCm());
        Integer heightCm = normalizePositiveDimension(req.getHeightCm());

        EstimateRequest estimateRequest = EstimateRequest.builder()
                .senderProvince(req.getSenderProvince().trim())
                .receiverProvince(req.getReceiverProvince().trim())
                .actualWeightKg(req.getActualWeightKg())
                .lengthCm(lengthCm)
                .widthCm(widthCm)
                .heightCm(heightCm)
                .codAmount(codAmount)
                .serviceTypeId(req.getServiceTypeId())
                .build();

        EstimateResponse estimate = pricingService.estimate(estimateRequest);
        if (estimate == null || estimate.options() == null || estimate.options().isEmpty()) {
            throw new BusinessException("Không thể tính cước cho gói dịch vụ đã chọn");
        }
        ServiceOptionQuote quote = estimate.options().get(0);

        Hub originHub = pricingService.resolveHubForProvince(req.getSenderProvince());
        Hub destHub = pricingService.resolveHubForProvince(req.getReceiverProvince());

        ServiceType serviceType = serviceTypeRepository.findById(req.getServiceTypeId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy gói dịch vụ với ID: " + req.getServiceTypeId()));
        if (!Boolean.TRUE.equals(serviceType.getActive())) {
            throw new BusinessException("Gói dịch vụ '" + serviceType.getName() + "' hiện đang tạm ngưng hoạt động");
        }

        // BR-04: If receiver pays shipping fee, force payment method to CASH
        PayerType payerType = req.getPayerType() != null ? req.getPayerType() : PayerType.SENDER;
        PaymentMethod paymentMethod = req.getPaymentMethod() != null ? req.getPaymentMethod() : PaymentMethod.CASH;
        if (payerType == PayerType.RECEIVER) {
            paymentMethod = PaymentMethod.CASH;
        }

        ShipmentStatus initialStatus = (payerType == PayerType.SENDER && paymentMethod == PaymentMethod.VNPAY)
                ? ShipmentStatus.PENDING_PAYMENT
                : ShipmentStatus.CREATED;
        PaymentStatus initialPaymentStatus = PaymentStatus.UNPAID;

        boolean hasCodToCollect = codAmount.compareTo(BigDecimal.ZERO) > 0 || payerType == PayerType.RECEIVER;
        CodStatus initialCodStatus = hasCodToCollect ? CodStatus.PENDING : CodStatus.NONE;

        InspectionPolicy inspectionPolicy = req.getInspectionPolicy() != null
                ? req.getInspectionPolicy()
                : InspectionPolicy.ALLOW_VIEW_NO_TRY;

        String trackingCode = generateTrackingCode();

        Shipment shipment = Shipment.builder()
                .trackingCode(trackingCode)
                .customer(customer)
                .serviceType(serviceType)
                .senderName(req.getSenderName().trim())
                .senderPhone(req.getSenderPhone().trim())
                .senderProvince(req.getSenderProvince().trim())
                .senderDistrict(req.getSenderDistrict().trim())
                .senderAddress(req.getSenderAddress().trim())
                .receiverName(req.getReceiverName().trim())
                .receiverPhone(req.getReceiverPhone().trim())
                .receiverProvince(req.getReceiverProvince().trim())
                .receiverDistrict(req.getReceiverDistrict().trim())
                .receiverAddress(req.getReceiverAddress().trim())
                .originHub(originHub)
                .destHub(destHub)
                .currentHub(originHub)
                .itemName(req.getItemName().trim())
                .actualWeightKg(req.getActualWeightKg())
                .lengthCm(lengthCm)
                .widthCm(widthCm)
                .heightCm(heightCm)
                .chargeableWeightKg(estimate.chargeableWeightKg())
                .isFragile(req.isFragile())
                .inspectionPolicy(inspectionPolicy)
                .note(normalizeOptionalText(req.getNote()))
                .shippingFee(quote.shippingFee())
                .codFee(quote.codFee())
                .totalFee(quote.totalFee())
                .codAmount(codAmount)
                .payerType(payerType)
                .paymentMethod(paymentMethod)
                .paymentStatus(initialPaymentStatus)
                .status(initialStatus)
                .failedDeliveryCount(0)
                .codStatus(initialCodStatus)
                .build();

        Shipment saved = shipmentRepository.save(shipment);

        String logTitle;
        String logDesc;
        if (initialStatus == ShipmentStatus.PENDING_PAYMENT) {
            logTitle = "Khởi tạo vận đơn — Chờ thanh toán VNPay";
            logDesc = String.format(
                    "Vận đơn %s tuyến %s -> %s đã được tạo, đang chờ thanh toán cước trực tuyến qua VNPay.",
                    saved.getTrackingCode(), originHub.getCode(), destHub.getCode()
            );
        } else {
            logTitle = "Khởi tạo vận đơn thành công";
            logDesc = String.format(
                    "Vận đơn %s tuyến %s -> %s đã tiếp nhận tại %s (%s), đang chờ điều phối lấy hàng.",
                    saved.getTrackingCode(), originHub.getCode(), destHub.getCode(),
                    originHub.getName(), originHub.getCode()
            );
        }

        shipmentService.recordTrackingLog(saved, saved.getStatus(), originHub, customer, logTitle, logDesc);
        return saved;
    }

    @Transactional(readOnly = true)
    public CustomerDashboardSummary getDashboardSummary(Long customerId) {
        long totalShipments = shipmentRepository.countByCustomerId(customerId);
        long pendingPaymentCount = shipmentRepository.countByCustomerIdAndStatus(customerId, ShipmentStatus.PENDING_PAYMENT);
        long inProgressCount = shipmentRepository.countByCustomerIdAndStatusIn(customerId, IN_PROGRESS_STATUSES);
        long deliveredCount = shipmentRepository.countByCustomerIdAndStatus(customerId, ShipmentStatus.DELIVERED);
        long failedOrReturnedCount = shipmentRepository.countByCustomerIdAndStatusIn(customerId, FAILED_RETURNED_STATUSES);
        long cancelledCount = shipmentRepository.countByCustomerIdAndStatus(customerId, ShipmentStatus.CANCELLED);

        Specification<Shipment> pendingCodSpec = (root, query, cb) -> cb.and(
                cb.equal(root.get("customer").get("id"), customerId),
                cb.equal(root.get("status"), ShipmentStatus.DELIVERED),
                root.get("codStatus").in(PENDING_COD_SETTLEMENT_STATUSES)
        );
        List<Shipment> pendingCodShipments = shipmentRepository.findAll(pendingCodSpec);
        BigDecimal pendingCodAmount = pendingCodShipments.stream()
                .map(Shipment::getCodAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Shipment> recentShipments = shipmentRepository.findTop10ByCustomerIdOrderByCreatedAtDesc(customerId);

        return new CustomerDashboardSummary(
                totalShipments,
                pendingPaymentCount,
                inProgressCount,
                deliveredCount,
                failedOrReturnedCount,
                cancelledCount,
                pendingCodAmount,
                recentShipments
        );
    }

    @Transactional(readOnly = true)
    public Page<Shipment> searchCustomerShipments(Long customerId,
                                                  String tab,
                                                  String keyword,
                                                  LocalDate fromDate,
                                                  LocalDate toDate,
                                                  Pageable pageable) {
        String normalizedTab = normalizeTab(tab);
        List<ShipmentStatus> tabStatuses = resolveStatusesForTab(normalizedTab);

        Specification<Shipment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("customer").get("id"), customerId));

            if (tabStatuses != null && !tabStatuses.isEmpty()) {
                predicates.add(root.get("status").in(tabStatuses));
            }

            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                Predicate matchTracking = cb.like(cb.lower(root.get("trackingCode")), pattern);
                Predicate matchReceiverName = cb.like(cb.lower(root.get("receiverName")), pattern);
                Predicate matchReceiverPhone = cb.like(cb.lower(root.get("receiverPhone")), pattern);
                Predicate matchItemName = cb.like(cb.lower(root.get("itemName")), pattern);
                predicates.add(cb.or(matchTracking, matchReceiverName, matchReceiverPhone, matchItemName));
            }

            if (fromDate != null) {
                OffsetDateTime fromStart = fromDate.atStartOfDay(VIETNAM_ZONE).toOffsetDateTime();
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromStart));
            }

            if (toDate != null) {
                OffsetDateTime toEndExclusive = toDate.plusDays(1).atStartOfDay(VIETNAM_ZONE).toOffsetDateTime();
                predicates.add(cb.lessThan(root.get("createdAt"), toEndExclusive));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return shipmentRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getTabCounts(Long customerId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put(TAB_ALL, shipmentRepository.countByCustomerId(customerId));
        counts.put(TAB_PENDING_PAYMENT, shipmentRepository.countByCustomerIdAndStatus(customerId, ShipmentStatus.PENDING_PAYMENT));
        counts.put(TAB_WAITING_PICKUP, shipmentRepository.countByCustomerIdAndStatusIn(customerId, WAITING_PICKUP_STATUSES));
        counts.put(TAB_IN_TRANSIT, shipmentRepository.countByCustomerIdAndStatusIn(customerId, IN_TRANSIT_STATUSES));
        counts.put(TAB_DELIVERING, shipmentRepository.countByCustomerIdAndStatus(customerId, ShipmentStatus.OUT_FOR_DELIVERY));
        counts.put(TAB_DELIVERED, shipmentRepository.countByCustomerIdAndStatus(customerId, ShipmentStatus.DELIVERED));
        counts.put(TAB_FAILED_RETURNED, shipmentRepository.countByCustomerIdAndStatusIn(customerId, FAILED_RETURNED_STATUSES));
        counts.put(TAB_CANCELLED, shipmentRepository.countByCustomerIdAndStatus(customerId, ShipmentStatus.CANCELLED));
        return counts;
    }

    @Transactional(readOnly = true)
    public Shipment getCustomerShipmentDetail(Long customerId, Long shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vận đơn với ID: " + shipmentId));

        if (shipment.getCustomer() == null || !Objects.equals(shipment.getCustomer().getId(), customerId)) {
            throw new AccessDeniedException("Bạn không có quyền truy cập vận đơn này");
        }
        return shipment;
    }

    @Transactional(readOnly = true)
    public List<ShipmentTrackingLog> getTrackingLogs(Long shipmentId) {
        return trackingLogRepository.findByShipmentIdOrderByCreatedAtDescIdDesc(shipmentId);
    }

    public String normalizeTab(String tab) {
        if (tab == null || tab.isBlank()) {
            return TAB_ALL;
        }
        String normalized = tab.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case TAB_PENDING_PAYMENT,
                 TAB_WAITING_PICKUP,
                 TAB_IN_TRANSIT,
                 TAB_DELIVERING,
                 TAB_DELIVERED,
                 TAB_FAILED_RETURNED,
                 TAB_CANCELLED -> normalized;
            default -> TAB_ALL;
        };
    }

    private List<ShipmentStatus> resolveStatusesForTab(String normalizedTab) {
        return switch (normalizedTab) {
            case TAB_PENDING_PAYMENT -> List.of(ShipmentStatus.PENDING_PAYMENT);
            case TAB_WAITING_PICKUP -> WAITING_PICKUP_STATUSES;
            case TAB_IN_TRANSIT -> IN_TRANSIT_STATUSES;
            case TAB_DELIVERING -> List.of(ShipmentStatus.OUT_FOR_DELIVERY);
            case TAB_DELIVERED -> List.of(ShipmentStatus.DELIVERED);
            case TAB_FAILED_RETURNED -> FAILED_RETURNED_STATUSES;
            case TAB_CANCELLED -> List.of(ShipmentStatus.CANCELLED);
            default -> null;
        };
    }

    private Integer normalizePositiveDimension(Integer value) {
        return (value != null && value > 0) ? value : null;
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
