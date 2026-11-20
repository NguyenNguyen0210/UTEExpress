package com.uteexpress.controller.web;

import com.uteexpress.dto.staff.StaffDashboardSummary;
import com.uteexpress.entity.Hub;
import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.ShipmentTrackingLog;
import com.uteexpress.entity.User;
import com.uteexpress.enums.PaymentStatus;
import com.uteexpress.enums.Role;
import com.uteexpress.enums.ShipmentStatus;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.exception.ResourceNotFoundException;
import com.uteexpress.repository.UserRepository;
import com.uteexpress.security.CustomUserDetails;
import com.uteexpress.service.ShipmentService;
import com.uteexpress.service.ShipmentStateMachine;
import com.uteexpress.service.StaffShipmentQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Controller
@RequestMapping("/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffShipmentQueryService staffShipmentQueryService;
    private final ShipmentService shipmentService;
    private final ShipmentStateMachine stateMachine;
    private final UserRepository userRepository;

    private User getCurrentUser(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getId() == null) {
            throw new AccessDeniedException("Phiên đăng nhập không hợp lệ");
        }
        return userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản hiện tại"));
    }

    @GetMapping
    public String dashboard(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        User actor = getCurrentUser(userDetails);
        StaffDashboardSummary summary = staffShipmentQueryService.getStaffDashboard(actor);
        model.addAttribute("summary", summary);
        model.addAttribute("actor", actor);
        return "staff/dashboard";
    }

    @GetMapping("/shipments")
    public String listShipments(@RequestParam(defaultValue = "all") String tab,
                                @RequestParam(required = false) String keyword,
                                @RequestParam(defaultValue = "0") int page,
                                @AuthenticationPrincipal CustomUserDetails userDetails,
                                Model model) {
        User actor = getCurrentUser(userDetails);
        String activeTab = staffShipmentQueryService.normalizeTab(tab);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, 12, Sort.by(Sort.Direction.DESC, "updatedAt"));

        Page<Shipment> shipmentsPage = staffShipmentQueryService.searchStaffShipments(actor, activeTab, keyword, pageable);
        Map<String, Long> tabCounts = staffShipmentQueryService.getStaffTabCounts(actor);

        model.addAttribute("shipmentsPage", shipmentsPage);
        model.addAttribute("tabCounts", tabCounts);
        model.addAttribute("activeTab", activeTab);
        model.addAttribute("keyword", keyword);
        model.addAttribute("hubLabel", staffShipmentQueryService.resolveHubLabel(actor));
        model.addAttribute("actor", actor);
        return "staff/shipment-list";
    }

    @GetMapping("/shipments/{id}")
    public String shipmentDetail(@PathVariable Long id,
                                 @AuthenticationPrincipal CustomUserDetails userDetails,
                                 Model model) {
        User actor = getCurrentUser(userDetails);
        Shipment shipment = shipmentService.getShipmentById(id);
        shipmentService.assertCanViewShipment(shipment, actor);

        List<ShipmentTrackingLog> trackingLogs = staffShipmentQueryService.getTrackingLogs(shipment.getId());
        boolean isSameHub = shipment.getOriginHub() != null
                && shipment.getDestHub() != null
                && Objects.equals(shipment.getOriginHub().getId(), shipment.getDestHub().getId());

        BigDecimal receiverCollectAmount = shipmentService.calculateReceiverCollectAmount(shipment);
        int failedCount = shipment.getFailedDeliveryCount() != null ? shipment.getFailedDeliveryCount() : 0;
        boolean canRedeliver = failedCount < ShipmentStateMachine.MAX_FAILED_DELIVERY_ATTEMPTS;

        boolean ownsOriginHub = ownsHub(actor, shipment.getOriginHub());
        boolean ownsDestHub = ownsHub(actor, shipment.getDestHub());
        ShipmentStatus status = shipment.getStatus();

        boolean canAssignPickup = ownsOriginHub && status == ShipmentStatus.CREATED;
        boolean canArriveOrigin = ownsOriginHub && status == ShipmentStatus.PICKED_UP;
        boolean canDispatchTransit = ownsOriginHub && status == ShipmentStatus.IN_ORIGIN_HUB && !isSameHub;
        boolean canMoveToReadyDelivery = ownsDestHub && status == ShipmentStatus.IN_ORIGIN_HUB && isSameHub;
        boolean canReceiveFromTransit = ownsDestHub && status == ShipmentStatus.IN_TRANSIT;
        boolean canArriveDest = canMoveToReadyDelivery || canReceiveFromTransit;
        boolean canAssignDelivery = ownsDestHub
                && (status == ShipmentStatus.IN_DEST_HUB || (status == ShipmentStatus.DELIVERY_FAILED && canRedeliver));
        boolean canStartReturn = ownsDestHub && status == ShipmentStatus.DELIVERY_FAILED;
        boolean canCompleteReturn = (ownsOriginHub || ownsDestHub) && status == ShipmentStatus.RETURNING;
        boolean canCancel = stateMachine.isCancelable(status);

        List<User> originShippers = shipment.getOriginHub() != null
                ? staffShipmentQueryService.getActiveShippersForHub(shipment.getOriginHub().getId())
                : List.of();
        List<User> destShippers = shipment.getDestHub() != null
                ? staffShipmentQueryService.getActiveShippersForHub(shipment.getDestHub().getId())
                : List.of();

        model.addAttribute("shipment", shipment);
        model.addAttribute("trackingLogs", trackingLogs);
        model.addAttribute("isSameHub", isSameHub);
        model.addAttribute("receiverCollectAmount", receiverCollectAmount);
        model.addAttribute("canCancel", canCancel);
        model.addAttribute("canRedeliver", canRedeliver);
        model.addAttribute("originShippers", originShippers);
        model.addAttribute("destShippers", destShippers);

        model.addAttribute("canAssignPickup", canAssignPickup);
        model.addAttribute("canArriveOrigin", canArriveOrigin);
        model.addAttribute("canDispatchTransit", canDispatchTransit);
        model.addAttribute("canMoveToReadyDelivery", canMoveToReadyDelivery);
        model.addAttribute("canReceiveFromTransit", canReceiveFromTransit);
        model.addAttribute("canArriveDest", canArriveDest);
        model.addAttribute("canAssignDelivery", canAssignDelivery);
        model.addAttribute("canStartReturn", canStartReturn);
        model.addAttribute("canCompleteReturn", canCompleteReturn);
        model.addAttribute("hasOperationalAction",
                canAssignPickup || canArriveOrigin || canDispatchTransit || canArriveDest
                        || canAssignDelivery || canStartReturn || canCompleteReturn);

        model.addAttribute("actor", actor);
        model.addAttribute("currentUserId", actor.getId());
        return "staff/shipment-detail";
    }

    @PostMapping("/shipments/{id}/assign-pickup")
    public String assignPickup(@PathVariable Long id,
                               @RequestParam(required = false) Long shipperId,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               RedirectAttributes redirectAttributes) {
        User actor = getCurrentUser(userDetails);
        try {
            Shipment updated = shipmentService.assignPickupShipper(id, shipperId, actor);
            String shipperName = updated.getPickupShipper() != null ? updated.getPickupShipper().getFullName() : "";
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã phân công Shipper " + shipperName + " lấy hàng cho vận đơn " + updated.getTrackingCode() + ".");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/staff/shipments/" + id;
    }

    @PostMapping("/shipments/{id}/arrive-origin")
    public String arriveOrigin(@PathVariable Long id,
                               @RequestParam(required = false) String note,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               RedirectAttributes redirectAttributes) {
        User actor = getCurrentUser(userDetails);
        try {
            Shipment updated = shipmentService.arriveAtOriginHub(id, actor, note);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã xác nhận nhập kho bưu cục gửi (" + updated.getOriginHub().getCode() + ") cho vận đơn "
                            + updated.getTrackingCode() + ".");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/staff/shipments/" + id;
    }

    @PostMapping("/shipments/{id}/dispatch-transit")
    public String dispatchTransit(@PathVariable Long id,
                                  @RequestParam(required = false) String note,
                                  @AuthenticationPrincipal CustomUserDetails userDetails,
                                  RedirectAttributes redirectAttributes) {
        User actor = getCurrentUser(userDetails);
        try {
            Shipment updated = shipmentService.dispatchTransit(id, actor, note);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã xuất kho trung chuyển liên tỉnh (" + updated.getOriginHub().getCode()
                            + " → " + updated.getDestHub().getCode() + ") cho vận đơn " + updated.getTrackingCode() + ".");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/staff/shipments/" + id;
    }

    @PostMapping("/shipments/{id}/arrive-dest")
    public String arriveDest(@PathVariable Long id,
                             @RequestParam(required = false) String note,
                             @AuthenticationPrincipal CustomUserDetails userDetails,
                             RedirectAttributes redirectAttributes) {
        User actor = getCurrentUser(userDetails);
        try {
            Shipment updated = shipmentService.arriveAtDestHub(id, actor, note);
            boolean isSameHub = updated.getOriginHub() != null && updated.getDestHub() != null
                    && Objects.equals(updated.getOriginHub().getId(), updated.getDestHub().getId());
            if (isSameHub) {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Đã chuyển vận đơn nội vùng " + updated.getTrackingCode() + " sang sẵn sàng phân công giao hàng.");
            } else {
                redirectAttributes.addFlashAttribute("successMessage",
                        "Đã xác nhận nhập kho bưu cục nhận (" + updated.getDestHub().getCode() + ") cho vận đơn "
                                + updated.getTrackingCode() + ".");
            }
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/staff/shipments/" + id;
    }

    @PostMapping("/shipments/{id}/assign-delivery")
    public String assignDelivery(@PathVariable Long id,
                                 @RequestParam(required = false) Long shipperId,
                                 @AuthenticationPrincipal CustomUserDetails userDetails,
                                 RedirectAttributes redirectAttributes) {
        User actor = getCurrentUser(userDetails);
        try {
            Shipment updated = shipmentService.assignDeliveryShipper(id, shipperId, actor);
            String shipperName = updated.getDeliveryShipper() != null ? updated.getDeliveryShipper().getFullName() : "";
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã phân công Shipper " + shipperName + " đi giao vận đơn " + updated.getTrackingCode() + ".");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/staff/shipments/" + id;
    }

    @PostMapping("/shipments/{id}/start-return")
    public String startReturn(@PathVariable Long id,
                              @RequestParam(required = false) String reason,
                              @AuthenticationPrincipal CustomUserDetails userDetails,
                              RedirectAttributes redirectAttributes) {
        User actor = getCurrentUser(userDetails);
        try {
            Shipment updated = shipmentService.startReturn(id, actor, reason);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã kích hoạt quy trình hoàn hàng cho vận đơn " + updated.getTrackingCode() + ".");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/staff/shipments/" + id;
    }

    @PostMapping("/shipments/{id}/complete-return")
    public String completeReturn(@PathVariable Long id,
                                 @RequestParam(required = false) String note,
                                 @AuthenticationPrincipal CustomUserDetails userDetails,
                                 RedirectAttributes redirectAttributes) {
        User actor = getCurrentUser(userDetails);
        try {
            Shipment updated = shipmentService.completeReturn(id, actor, note);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã xác nhận hoàn trả vận đơn " + updated.getTrackingCode() + " về người gửi thành công.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/staff/shipments/" + id;
    }

    @PostMapping("/shipments/{id}/cancel")
    public String cancelShipment(@PathVariable Long id,
                                 @RequestParam(required = false) String reason,
                                 @AuthenticationPrincipal CustomUserDetails userDetails,
                                 RedirectAttributes redirectAttributes) {
        User actor = getCurrentUser(userDetails);
        try {
            Shipment updated = shipmentService.cancelShipment(id, actor, reason);
            String msg = "Đã hủy vận đơn " + updated.getTrackingCode() + " thành công.";
            if (updated.getPaymentStatus() == PaymentStatus.REFUND_PENDING) {
                msg += " Đơn đã thanh toán trực tuyến được chuyển sang trạng thái chờ hoàn tiền.";
            }
            redirectAttributes.addFlashAttribute("successMessage", msg);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/staff/shipments/" + id;
    }

    private boolean ownsHub(User actor, Hub hub) {
        if (actor == null || actor.getRole() == null) {
            return false;
        }
        if (actor.getRole() == Role.ADMIN) {
            return true;
        }
        return actor.getRole() == Role.STAFF
                && actor.getHub() != null
                && hub != null
                && Objects.equals(actor.getHub().getId(), hub.getId());
    }
}
