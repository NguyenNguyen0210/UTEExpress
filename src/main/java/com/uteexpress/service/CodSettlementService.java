package com.uteexpress.service;

import com.uteexpress.dto.cod.CustomerPendingCodDto;
import com.uteexpress.dto.cod.SettlementDetailView;
import com.uteexpress.dto.cod.ShipperCodGroupDto;
import com.uteexpress.entity.CodSettlement;
import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.User;
import com.uteexpress.enums.CodStatus;
import com.uteexpress.enums.PayerType;
import com.uteexpress.enums.PaymentStatus;
import com.uteexpress.enums.Role;
import com.uteexpress.enums.SettlementStatus;
import com.uteexpress.enums.ShipmentStatus;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.exception.ResourceNotFoundException;
import com.uteexpress.repository.CodSettlementRepository;
import com.uteexpress.repository.ShipmentRepository;
import com.uteexpress.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CodSettlementService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter CODE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");
    private static final String CODE_CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789";
    private static final int CODE_RANDOM_LENGTH = 4;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final List<CodStatus> PENDING_COD_STATUSES = List.of(
            CodStatus.HELD_BY_SHIPPER,
            CodStatus.COLLECTED_BY_HUB
    );

    private final ShipmentRepository shipmentRepository;
    private final CodSettlementRepository codSettlementRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ShipmentService shipmentService;

    // ==================== SHIPPER VIEW (US-7.1) ====================

    @Transactional(readOnly = true)
    public List<Shipment> getShipperHeldShipments(Long shipperId) {
        if (shipperId == null) {
            return List.of();
        }
        return shipmentRepository.findByDeliveryShipperIdAndCodStatusOrderByDeliveredAtDesc(
                shipperId, CodStatus.HELD_BY_SHIPPER
        );
    }

    public BigDecimal calculateTotalCashHeldByShipper(List<Shipment> shipments) {
        if (shipments == null || shipments.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Shipment shipment : shipments) {
            if (shipment != null) {
                total = total.add(safeAmount(shipmentService.calculateReceiverCollectAmount(shipment)));
            }
        }
        return total;
    }

    // ==================== STAFF COD COLLECTION (US-7.1) ====================

    @Transactional(readOnly = true)
    public List<ShipperCodGroupDto> getHeldCodGroupedByShipper(User staffActor) {
        if (staffActor == null || staffActor.getRole() == null) {
            throw new AccessDeniedException("Bạn không có quyền xem danh sách thu tiền COD");
        }

        List<Shipment> heldShipments;
        if (staffActor.getRole() == Role.ADMIN) {
            Specification<Shipment> spec = (root, query, cb) ->
                    cb.equal(root.get("codStatus"), CodStatus.HELD_BY_SHIPPER);
            heldShipments = shipmentRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "deliveredAt"));
        } else if (staffActor.getRole() == Role.STAFF) {
            if (staffActor.getHub() == null || staffActor.getHub().getId() == null) {
                return List.of();
            }
            heldShipments = shipmentRepository.findByDestHubIdAndCodStatusOrderByDeliveredAtDesc(
                    staffActor.getHub().getId(), CodStatus.HELD_BY_SHIPPER
            );
        } else {
            throw new AccessDeniedException("Bạn không có quyền truy cập chức năng thu tiền COD từ Shipper");
        }

        Map<Long, List<Shipment>> grouped = new LinkedHashMap<>();
        Map<Long, User> shipperMap = new LinkedHashMap<>();
        for (Shipment shipment : heldShipments) {
            User shipper = shipment.getDeliveryShipper();
            if (shipper == null || shipper.getId() == null) {
                continue;
            }
            shipperMap.putIfAbsent(shipper.getId(), shipper);
            grouped.computeIfAbsent(shipper.getId(), k -> new ArrayList<>()).add(shipment);
        }

        List<ShipperCodGroupDto> result = new ArrayList<>();
        for (Map.Entry<Long, List<Shipment>> entry : grouped.entrySet()) {
            User shipper = shipperMap.get(entry.getKey());
            List<Shipment> shipperShipments = entry.getValue();

            BigDecimal totalCod = calculateTotalCodAmount(shipperShipments);
            BigDecimal totalCollect = calculateTotalCashHeldByShipper(shipperShipments);
            String hubCode = resolveShipperHubCode(shipper, shipperShipments);

            result.add(new ShipperCodGroupDto(
                    shipper.getId(),
                    shipper.getFullName(),
                    shipper.getPhone(),
                    hubCode,
                    shipperShipments,
                    totalCod,
                    totalCollect
            ));
        }
        return result;
    }

    @Transactional
    public int collectCodFromShipper(List<Long> shipmentIds, User staffActor) {
        if (shipmentIds == null || shipmentIds.isEmpty()) {
            throw new BusinessException("Vui lòng chọn ít nhất một vận đơn để xác nhận thu tiền COD");
        }

        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (Long id : shipmentIds) {
            if (id != null) {
                uniqueIds.add(id);
            }
        }
        if (uniqueIds.isEmpty()) {
            throw new BusinessException("Vui lòng chọn ít nhất một vận đơn để xác nhận thu tiền COD");
        }

        OffsetDateTime now = OffsetDateTime.now();
        int collectedCount = 0;

        for (Long shipmentId : uniqueIds) {
            Shipment shipment = shipmentRepository.findById(shipmentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vận đơn với ID: " + shipmentId));

            Long destHubId = shipment.getDestHub() != null ? shipment.getDestHub().getId() : null;
            shipmentService.assertStaffOwnsHub(staffActor, destHubId);

            if (shipment.getStatus() != ShipmentStatus.DELIVERED) {
                throw new BusinessException("Vận đơn " + shipment.getTrackingCode()
                        + " chưa giao thành công, không thể xác nhận thu tiền COD");
            }
            if (shipment.getCodStatus() != CodStatus.HELD_BY_SHIPPER) {
                throw new BusinessException("Vận đơn " + shipment.getTrackingCode()
                        + " không ở trạng thái Shipper đang giữ tiền COD");
            }

            shipment.setCodStatus(CodStatus.COLLECTED_BY_HUB);
            shipment.setCodCollectedBy(staffActor);
            shipment.setCodCollectedAt(now);
            shipmentRepository.save(shipment);
            collectedCount++;
        }

        return collectedCount;
    }

    // ==================== ADMIN COD SETTLEMENT (BR-09, US-7.2) ====================

    @Transactional(readOnly = true)
    public List<CustomerPendingCodDto> getCustomersReadyForSettlement() {
        List<Shipment> eligibleShipments = shipmentRepository
                .findByStatusAndCodStatusAndSettlementIsNullOrderByDeliveredAtAsc(
                        ShipmentStatus.DELIVERED,
                        CodStatus.COLLECTED_BY_HUB
                );

        Map<Long, List<Shipment>> byCustomer = new LinkedHashMap<>();
        Map<Long, User> customerMap = new LinkedHashMap<>();

        for (Shipment shipment : eligibleShipments) {
            User customer = shipment.getCustomer();
            if (customer == null || customer.getId() == null) {
                continue;
            }
            customerMap.putIfAbsent(customer.getId(), customer);
            byCustomer.computeIfAbsent(customer.getId(), k -> new ArrayList<>()).add(shipment);
        }

        List<CustomerPendingCodDto> result = new ArrayList<>();
        for (Map.Entry<Long, List<Shipment>> entry : byCustomer.entrySet()) {
            User customer = customerMap.get(entry.getKey());
            List<Shipment> shipments = entry.getValue();

            BigDecimal totalCod = calculateTotalCodAmount(shipments);
            BigDecimal totalDeduction = calculateShippingDeduction(shipments);
            BigDecimal netAmount = totalCod.subtract(totalDeduction);

            result.add(new CustomerPendingCodDto(
                    customer.getId(),
                    customer.getFullName(),
                    customer.getEmail(),
                    customer.getBankName(),
                    customer.getBankAccountNumber(),
                    customer.getBankAccountHolder(),
                    shipments,
                    totalCod,
                    totalDeduction,
                    netAmount
            ));
        }

        return result;
    }

    @Transactional
    public CodSettlement createSettlement(Long customerId, String note, User adminActor) {
        assertAdmin(adminActor);
        if (customerId == null) {
            throw new BusinessException("Vui lòng chọn khách hàng cần lập phiếu đối soát COD");
        }

        List<Shipment> eligible = shipmentRepository
                .findByCustomerIdAndStatusAndCodStatusAndSettlementIsNullOrderByDeliveredAtAsc(
                        customerId,
                        ShipmentStatus.DELIVERED,
                        CodStatus.COLLECTED_BY_HUB
                );

        if (eligible == null || eligible.isEmpty()) {
            throw new BusinessException("Khách hàng không có vận đơn nào đủ điều kiện đối soát COD");
        }

        User customer = userRepository.findById(customerId)
                .orElseGet(() -> eligible.get(0).getCustomer());
        if (customer == null) {
            throw new ResourceNotFoundException("Không tìm thấy thông tin khách hàng với ID: " + customerId);
        }

        BigDecimal totalCodAmount = calculateTotalCodAmount(eligible);
        BigDecimal totalShippingDeduction = calculateShippingDeduction(eligible);
        BigDecimal netAmount = totalCodAmount.subtract(totalShippingDeduction);
        String settlementCode = generateSettlementCode();

        CodSettlement settlement = CodSettlement.builder()
                .settlementCode(settlementCode)
                .customer(customer)
                .totalCodAmount(totalCodAmount)
                .totalShippingDeduction(totalShippingDeduction)
                .netAmount(netAmount)
                .bankName(customer.getBankName())
                .bankAccountNumber(customer.getBankAccountNumber())
                .bankAccountHolder(customer.getBankAccountHolder())
                .status(SettlementStatus.PENDING)
                .note(normalizeOptionalText(note))
                .createdBy(adminActor)
                .createdAt(OffsetDateTime.now())
                .build();

        CodSettlement savedSettlement = codSettlementRepository.save(settlement);

        for (Shipment shipment : eligible) {
            shipment.setSettlement(savedSettlement);
        }
        shipmentRepository.saveAll(eligible);

        return savedSettlement;
    }

    @Transactional
    public CodSettlement markSettled(Long settlementId, String transactionRef, User adminActor) {
        assertAdmin(adminActor);
        if (settlementId == null) {
            throw new ResourceNotFoundException("Mã phiếu đối soát không hợp lệ");
        }

        CodSettlement settlement = codSettlementRepository.findById(settlementId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu đối soát COD với ID: " + settlementId));

        if (settlement.getStatus() != SettlementStatus.PENDING) {
            throw new BusinessException("Phiếu đối soát này đã được thanh toán trước đó");
        }

        if (transactionRef == null || transactionRef.isBlank()) {
            throw new BusinessException("Vui lòng nhập mã giao dịch chuyển khoản tham chiếu");
        }

        OffsetDateTime now = OffsetDateTime.now();
        String trimmedRef = transactionRef.trim();

        settlement.setStatus(SettlementStatus.SETTLED);
        settlement.setTransactionRef(trimmedRef);
        settlement.setSettledAt(now);
        CodSettlement saved = codSettlementRepository.save(settlement);

        List<Shipment> shipments = shipmentRepository.findBySettlementIdOrderByDeliveredAtAsc(settlementId);
        for (Shipment shipment : shipments) {
            shipment.setCodStatus(CodStatus.SETTLED);
            if (shipment.getPayerType() == PayerType.SENDER
                    && shipment.getPaymentStatus() == PaymentStatus.UNPAID) {
                shipment.setPaymentStatus(PaymentStatus.PAID);
                shipment.setPaidAt(now);
            }
        }
        shipmentRepository.saveAll(shipments);

        notificationService.sendToUser(
                saved.getCustomer(),
                "Phiếu đối soát COD đã được thanh toán",
                "Phiếu " + saved.getSettlementCode() + " đã chuyển khoản "
                        + formatVnd(saved.getNetAmount()) + " đ (Mã GD: " + trimmedRef + ").",
                "/customer/cod"
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public List<SettlementDetailView> getAllSettlements() {
        List<CodSettlement> settlements = codSettlementRepository.findAllByOrderByCreatedAtDesc();
        return toDetailViews(settlements);
    }

    @Transactional(readOnly = true)
    public List<SettlementDetailView> getCustomerSettlements(Long customerId) {
        if (customerId == null) {
            return List.of();
        }
        List<CodSettlement> settlements = codSettlementRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        return toDetailViews(settlements);
    }

    @Transactional(readOnly = true)
    public List<Shipment> getCustomerPendingCodShipments(Long customerId) {
        if (customerId == null) {
            return List.of();
        }
        Specification<Shipment> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("customer").get("id"), customerId),
                cb.equal(root.get("status"), ShipmentStatus.DELIVERED),
                root.get("codStatus").in(PENDING_COD_STATUSES),
                cb.isNull(root.get("settlement"))
        );
        return shipmentRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "deliveredAt"));
    }

    public BigDecimal calculateTotalCodAmount(List<Shipment> shipments) {
        if (shipments == null || shipments.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return shipments.stream()
                .filter(Objects::nonNull)
                .map(Shipment::getCodAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateShippingDeduction(List<Shipment> shipments) {
        if (shipments == null || shipments.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Shipment shipment : shipments) {
            if (isDeductibleShippingFee(shipment)) {
                total = total.add(safeAmount(shipment.getTotalFee()));
            }
        }
        return total;
    }

    public boolean isDeductibleShippingFee(Shipment shipment) {
        return shipment != null
                && shipment.getPayerType() == PayerType.SENDER
                && shipment.getPaymentStatus() == PaymentStatus.UNPAID;
    }

    private List<SettlementDetailView> toDetailViews(List<CodSettlement> settlements) {
        List<SettlementDetailView> views = new ArrayList<>();
        for (CodSettlement settlement : settlements) {
            List<Shipment> shipments = shipmentRepository.findBySettlementIdOrderByDeliveredAtAsc(settlement.getId());
            views.add(new SettlementDetailView(settlement, shipments));
        }
        return views;
    }

    private String generateSettlementCode() {
        String datePart = LocalDate.now(VIETNAM_ZONE).format(CODE_DATE_FORMAT);
        String code;
        do {
            StringBuilder sb = new StringBuilder("COD").append(datePart);
            for (int i = 0; i < CODE_RANDOM_LENGTH; i++) {
                int idx = SECURE_RANDOM.nextInt(CODE_CHARSET.length());
                sb.append(CODE_CHARSET.charAt(idx));
            }
            code = sb.toString();
        } while (codSettlementRepository.findBySettlementCodeIgnoreCase(code).isPresent());
        return code;
    }

    private void assertAdmin(User actor) {
        if (actor == null || (actor.getRole() != null && actor.getRole() != Role.ADMIN)) {
            throw new AccessDeniedException("Chỉ Quản trị viên (ADMIN) mới có quyền thực hiện đối soát COD");
        }
    }

    private String resolveShipperHubCode(User shipper, List<Shipment> shipments) {
        if (shipper != null && shipper.getHub() != null && shipper.getHub().getCode() != null) {
            return shipper.getHub().getCode();
        }
        if (shipments != null && !shipments.isEmpty() && shipments.get(0).getDestHub() != null) {
            return shipments.get(0).getDestHub().getCode();
        }
        return "";
    }

    private BigDecimal safeAmount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String formatVnd(BigDecimal amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        DecimalFormat formatter = new DecimalFormat("#,##0", symbols);
        return formatter.format(safeAmount(amount));
    }
}
