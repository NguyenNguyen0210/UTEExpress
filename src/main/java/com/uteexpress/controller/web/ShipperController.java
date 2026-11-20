package com.uteexpress.controller.web;

import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.User;
import com.uteexpress.enums.ShipmentStatus;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.exception.ResourceNotFoundException;
import com.uteexpress.repository.UserRepository;
import com.uteexpress.security.CustomUserDetails;
import com.uteexpress.service.R2StorageService;
import com.uteexpress.service.ShipmentService;
import com.uteexpress.service.ShipmentStateMachine;
import com.uteexpress.service.StaffShipmentQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/shipper")
@RequiredArgsConstructor
public class ShipperController {

    private final StaffShipmentQueryService staffShipmentQueryService;
    private final ShipmentService shipmentService;
    private final ShipmentStateMachine shipmentStateMachine;
    private final R2StorageService r2StorageService;
    private final UserRepository userRepository;

    private User getCurrentUser(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getId() == null) {
            throw new AccessDeniedException("Phiên đăng nhập không hợp lệ");
        }
        return userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản Shipper"));
    }

    @GetMapping
    public String shipperIndex() {
        return "redirect:/shipper/tasks";
    }

    @GetMapping("/tasks")
    public String tasks(@RequestParam(defaultValue = "pickup") String tab,
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        Model model) {
        User shipper = getCurrentUser(userDetails);
        String activeTab = normalizeTaskTab(tab);

        List<Shipment> pickupTasks = staffShipmentQueryService.getShipperPickupTasks(shipper.getId());
        List<Shipment> deliveryTasks = staffShipmentQueryService.getShipperDeliveryTasks(shipper.getId());
        List<Shipment> doneTasks = staffShipmentQueryService.getShipperCompletedTasks(shipper.getId());

        Map<Long, BigDecimal> receiverCollectMap = new HashMap<>();
        populateCollectAmounts(receiverCollectMap, pickupTasks);
        populateCollectAmounts(receiverCollectMap, deliveryTasks);
        populateCollectAmounts(receiverCollectMap, doneTasks);

        model.addAttribute("activeTab", activeTab);
        model.addAttribute("shipper", shipper);
        model.addAttribute("pickupTasks", pickupTasks);
        model.addAttribute("deliveryTasks", deliveryTasks);
        model.addAttribute("doneTasks", doneTasks);
        model.addAttribute("pickupCount", pickupTasks.size());
        model.addAttribute("deliveryCount", deliveryTasks.size());
        model.addAttribute("doneCount", doneTasks.size());
        model.addAttribute("receiverCollectMap", receiverCollectMap);
        return "shipper/tasks";
    }

    @PostMapping("/shipments/{id}/pickup")
    public String markPickedUp(@PathVariable Long id,
                               @RequestParam(required = false) String note,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               RedirectAttributes redirectAttributes) {
        User shipper = getCurrentUser(userDetails);
        try {
            Shipment updated = shipmentService.markPickedUp(id, shipper, note);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã xác nhận lấy hàng thành công cho vận đơn " + updated.getTrackingCode() + ".");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/shipper/tasks?tab=pickup";
    }

    @PostMapping("/shipments/{id}/deliver")
    public String markDelivered(@PathVariable Long id,
                                @RequestParam(value = "podImage", required = false) MultipartFile podImage,
                                @RequestParam(required = false) String note,
                                @AuthenticationPrincipal CustomUserDetails userDetails,
                                RedirectAttributes redirectAttributes) {
        User shipper = getCurrentUser(userDetails);
        try {
            Shipment shipment = shipmentService.getShipmentById(id);
            shipmentService.assertAssignedShipper(shipment.getDeliveryShipper(), shipper, "giao hàng");
            shipmentStateMachine.validateTransition(shipment, ShipmentStatus.DELIVERED);

            String podImageKey = r2StorageService.uploadPodImage(podImage);
            Shipment updated = shipmentService.markDelivered(id, shipper, podImageKey, note);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã giao thành công vận đơn " + updated.getTrackingCode() + " và lưu ảnh POD!");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/shipper/tasks?tab=delivery";
    }

    @PostMapping("/shipments/{id}/fail")
    public String markDeliveryFailed(@PathVariable Long id,
                                     @RequestParam String reason,
                                     @RequestParam(required = false) String extraNote,
                                     @AuthenticationPrincipal CustomUserDetails userDetails,
                                     RedirectAttributes redirectAttributes) {
        User shipper = getCurrentUser(userDetails);
        String fullReason = combineReason(reason, extraNote);
        try {
            Shipment updated = shipmentService.markDeliveryFailed(id, shipper, fullReason);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật báo giao thất bại (Lần " + updated.getFailedDeliveryCount()
                            + "/3) cho vận đơn " + updated.getTrackingCode() + ".");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/shipper/tasks?tab=delivery";
    }

    private String normalizeTaskTab(String tab) {
        if (tab == null || tab.isBlank()) {
            return "pickup";
        }
        return switch (tab.trim().toLowerCase(Locale.ROOT)) {
            case "delivery" -> "delivery";
            case "done" -> "done";
            default -> "pickup";
        };
    }

    private void populateCollectAmounts(Map<Long, BigDecimal> map, List<Shipment> shipments) {
        for (Shipment shipment : shipments) {
            if (shipment != null && shipment.getId() != null) {
                map.put(shipment.getId(), shipmentService.calculateReceiverCollectAmount(shipment));
            }
        }
    }

    private String combineReason(String reason, String extraNote) {
        String trimmedReason = reason != null ? reason.trim() : "";
        String trimmedExtra = extraNote != null ? extraNote.trim() : "";
        if (!trimmedReason.isEmpty() && !trimmedExtra.isEmpty()) {
            return trimmedReason + " — " + trimmedExtra;
        }
        return !trimmedReason.isEmpty() ? trimmedReason : trimmedExtra;
    }
}
