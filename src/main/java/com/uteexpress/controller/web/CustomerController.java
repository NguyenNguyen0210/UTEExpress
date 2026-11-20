package com.uteexpress.controller.web;

import com.uteexpress.dto.customer.CreateShipmentRequest;
import com.uteexpress.dto.customer.CustomerDashboardSummary;
import com.uteexpress.entity.CustomerAddress;
import com.uteexpress.entity.ServiceType;
import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.ShipmentTrackingLog;
import com.uteexpress.entity.User;
import com.uteexpress.enums.InspectionPolicy;
import com.uteexpress.enums.PaymentStatus;
import com.uteexpress.enums.ShipmentStatus;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.security.CustomUserDetails;
import com.uteexpress.service.CustomerAddressService;
import com.uteexpress.service.CustomerProfileService;
import com.uteexpress.service.CustomerShipmentService;
import com.uteexpress.service.HubService;
import com.uteexpress.service.ServiceTypeService;
import com.uteexpress.service.ShipmentService;
import com.uteexpress.service.ShipmentStateMachine;
import com.uteexpress.service.VnPayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/customer")
@RequiredArgsConstructor
public class CustomerController {

    private static final int DEFAULT_PAGE_SIZE = 10;

    private final CustomerShipmentService customerShipmentService;
    private final CustomerAddressService customerAddressService;
    private final CustomerProfileService customerProfileService;
    private final HubService hubService;
    private final ServiceTypeService serviceTypeService;
    private final ShipmentService shipmentService;
    private final ShipmentStateMachine shipmentStateMachine;
    private final VnPayService vnPayService;

    @GetMapping({"", "/"})
    public String dashboard(@AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
        CustomerDashboardSummary summary = customerShipmentService.getDashboardSummary(currentUser.getId());
        model.addAttribute("summary", summary);
        return "customer/dashboard";
    }

    @GetMapping("/shipments")
    public String listShipments(@RequestParam(name = "tab", defaultValue = "all") String tab,
                                @RequestParam(name = "keyword", required = false) String keyword,
                                @RequestParam(name = "from", required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(name = "to", required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                @RequestParam(name = "page", defaultValue = "0") int page,
                                @AuthenticationPrincipal CustomUserDetails currentUser,
                                Model model) {
        String activeTab = customerShipmentService.normalizeTab(tab);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, DEFAULT_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt", "id"));

        Page<Shipment> shipmentsPage = customerShipmentService.searchCustomerShipments(
                currentUser.getId(), activeTab, keyword, from, to, pageable
        );
        Map<String, Long> tabCounts = customerShipmentService.getTabCounts(currentUser.getId());

        model.addAttribute("shipmentsPage", shipmentsPage);
        model.addAttribute("tabCounts", tabCounts);
        model.addAttribute("activeTab", activeTab);
        model.addAttribute("keyword", keyword != null ? keyword.trim() : "");
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        return "customer/shipment-list";
    }

    @GetMapping("/shipments/new")
    public String showCreateShipmentForm(@RequestParam(name = "serviceTypeId", required = false) Long serviceTypeId,
                                         @AuthenticationPrincipal CustomUserDetails currentUser,
                                         Model model) {
        List<CustomerAddress> addresses = customerAddressService.listAddresses(currentUser.getId());
        List<String> provinces = hubService.getActiveProvinces();
        List<ServiceType> serviceTypes = serviceTypeService.findActiveServices();

        if (!model.containsAttribute("shipmentForm")) {
            CreateShipmentRequest form = new CreateShipmentRequest();
            form.setActualWeightKg(new BigDecimal("1.0"));

            CustomerAddress defaultAddress = addresses.stream()
                    .filter(addr -> Boolean.TRUE.equals(addr.getIsDefault()))
                    .findFirst()
                    .orElse(addresses.isEmpty() ? null : addresses.get(0));

            if (defaultAddress != null) {
                form.setSenderName(defaultAddress.getContactName());
                form.setSenderPhone(defaultAddress.getContactPhone());
                form.setSenderProvince(defaultAddress.getProvince());
                form.setSenderDistrict(defaultAddress.getDistrict());
                form.setSenderAddress(defaultAddress.getAddressDetail());
            } else {
                User customer = customerProfileService.getProfile(currentUser.getId());
                form.setSenderName(customer.getFullName());
                form.setSenderPhone(customer.getPhone());
            }

            Long chosenServiceId = null;
            if (serviceTypeId != null) {
                boolean exists = serviceTypes.stream().anyMatch(svc -> svc.getId().equals(serviceTypeId));
                if (exists) {
                    chosenServiceId = serviceTypeId;
                }
            }
            if (chosenServiceId == null && !serviceTypes.isEmpty()) {
                chosenServiceId = serviceTypes.get(0).getId();
            }
            form.setServiceTypeId(chosenServiceId);

            model.addAttribute("shipmentForm", form);
        }

        populateCreateFormReferenceData(model, addresses, provinces, serviceTypes);
        return "customer/shipment-create";
    }

    @PostMapping("/shipments")
    public String createShipment(@Valid @ModelAttribute("shipmentForm") CreateShipmentRequest form,
                                 BindingResult bindingResult,
                                 @AuthenticationPrincipal CustomUserDetails currentUser,
                                 HttpServletRequest request,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("errorMessage", bindingResult.getAllErrors().get(0).getDefaultMessage());
            populateCreateFormReferenceData(
                    model,
                    customerAddressService.listAddresses(currentUser.getId()),
                    hubService.getActiveProvinces(),
                    serviceTypeService.findActiveServices()
            );
            return "customer/shipment-create";
        }

        try {
            Shipment saved = customerShipmentService.createShipment(currentUser.getId(), form);
            if (saved.getStatus() == ShipmentStatus.PENDING_PAYMENT) {
                String payUrl = vnPayService.createPaymentUrl(saved, resolveClientIp(request));
                return "redirect:" + payUrl;
            }
            redirectAttributes.addFlashAttribute("successMessage",
                    "Tạo vận đơn " + saved.getTrackingCode() + " thành công! "
                            + saved.getOriginHub().getName() + " sẽ sớm điều phối lấy hàng.");
            return "redirect:/customer/shipments/" + saved.getId();
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            populateCreateFormReferenceData(
                    model,
                    customerAddressService.listAddresses(currentUser.getId()),
                    hubService.getActiveProvinces(),
                    serviceTypeService.findActiveServices()
            );
            return "customer/shipment-create";
        }
    }

    @GetMapping("/shipments/{id}")
    public String shipmentDetail(@PathVariable Long id,
                                 @AuthenticationPrincipal CustomUserDetails currentUser,
                                 Model model) {
        Shipment shipment = customerShipmentService.getCustomerShipmentDetail(currentUser.getId(), id);
        List<ShipmentTrackingLog> trackingLogs = customerShipmentService.getTrackingLogs(shipment.getId());
        BigDecimal receiverCollectAmount = shipmentService.calculateReceiverCollectAmount(shipment);
        boolean canCancel = shipmentStateMachine.isCancelable(shipment.getStatus());

        model.addAttribute("shipment", shipment);
        model.addAttribute("trackingLogs", trackingLogs);
        model.addAttribute("receiverCollectAmount", receiverCollectAmount);
        model.addAttribute("canCancel", canCancel);
        model.addAttribute("currentUser", currentUser);
        return "customer/shipment-detail";
    }

    @PostMapping("/shipments/{id}/cancel")
    public String cancelShipment(@PathVariable Long id,
                                 @RequestParam(name = "reason", required = false) String reason,
                                 @AuthenticationPrincipal CustomUserDetails currentUser,
                                 RedirectAttributes redirectAttributes) {
        User customer = customerProfileService.getProfile(currentUser.getId());
        try {
            Shipment cancelled = shipmentService.cancelShipment(id, customer, reason);
            String message = "Đã hủy vận đơn " + cancelled.getTrackingCode() + " thành công.";
            if (cancelled.getPaymentStatus() == PaymentStatus.REFUND_PENDING) {
                message += " Đơn đã thanh toán VNPay đã được chuyển sang trạng thái Chờ hoàn tiền.";
            }
            redirectAttributes.addFlashAttribute("successMessage", message);
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/customer/shipments/" + id;
    }

    private void populateCreateFormReferenceData(Model model,
                                                 List<CustomerAddress> addresses,
                                                 List<String> provinces,
                                                 List<ServiceType> serviceTypes) {
        model.addAttribute("addresses", addresses);
        model.addAttribute("provinces", provinces);
        model.addAttribute("serviceTypes", serviceTypes);
        model.addAttribute("inspectionPolicies", InspectionPolicy.values());
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
