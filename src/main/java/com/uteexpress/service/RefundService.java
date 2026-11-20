package com.uteexpress.service;

import com.uteexpress.entity.Hub;
import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.User;
import com.uteexpress.enums.PaymentStatus;
import com.uteexpress.enums.Role;
import com.uteexpress.enums.ShipmentStatus;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.exception.ResourceNotFoundException;
import com.uteexpress.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentService shipmentService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<Shipment> getPendingRefundShipments() {
        Specification<Shipment> spec = (root, query, cb) ->
                cb.equal(root.get("paymentStatus"), PaymentStatus.REFUND_PENDING);
        return shipmentRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "updatedAt"));
    }

    @Transactional(readOnly = true)
    public List<Shipment> getCompletedRefundShipments() {
        Specification<Shipment> spec = (root, query, cb) ->
                cb.equal(root.get("paymentStatus"), PaymentStatus.REFUNDED);
        return shipmentRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "updatedAt"));
    }

    @Transactional
    public Shipment confirmVnPayRefund(Long shipmentId, String refundRef, User adminActor) {
        if (adminActor == null || adminActor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Chỉ quản trị viên (ADMIN) mới có quyền xác nhận hoàn tiền VNPay");
        }
        if (shipmentId == null) {
            throw new ResourceNotFoundException("Mã vận đơn không hợp lệ");
        }
        if (refundRef == null || refundRef.isBlank()) {
            throw new BusinessException("Vui lòng nhập mã giao dịch hoàn tiền tham chiếu");
        }

        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vận đơn với ID: " + shipmentId));

        if (shipment.getStatus() != ShipmentStatus.CANCELLED) {
            throw new BusinessException("Chỉ được xác nhận hoàn tiền cho vận đơn đã hủy");
        }
        if (shipment.getPaymentStatus() != PaymentStatus.REFUND_PENDING) {
            throw new BusinessException("Vận đơn " + shipment.getTrackingCode()
                    + " không ở trạng thái Chờ hoàn tiền (hiện tại: " + shipment.getPaymentStatus().getLabel() + ")");
        }

        String trimmedRef = refundRef.trim();
        shipment.setPaymentStatus(PaymentStatus.REFUNDED);
        Shipment saved = shipmentRepository.save(shipment);

        Hub logHub = saved.getCurrentHub() != null ? saved.getCurrentHub() : saved.getOriginHub();
        shipmentService.recordTrackingLog(
                saved,
                ShipmentStatus.CANCELLED,
                logHub,
                adminActor,
                "Đã hoàn tiền cước VNPay",
                "Admin đã xác nhận hoàn lại " + formatVnd(saved.getTotalFee())
                        + " đ về khách hàng (Mã GD hoàn tiền: " + trimmedRef + ")."
        );

        if (saved.getCustomer() != null) {
            notificationService.sendToUser(
                    saved.getCustomer(),
                    "Đã hoàn tiền cước VNPay",
                    "Vận đơn " + saved.getTrackingCode() + " đã được hoàn lại "
                            + formatVnd(saved.getTotalFee()) + " đ (Mã GD: " + trimmedRef + ").",
                    "/customer/shipments/" + saved.getId()
            );
        }

        return saved;
    }

    private String formatVnd(BigDecimal amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        DecimalFormat formatter = new DecimalFormat("#,##0", symbols);
        return formatter.format(amount != null ? amount : BigDecimal.ZERO);
    }
}
