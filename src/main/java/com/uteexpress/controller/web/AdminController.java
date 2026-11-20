package com.uteexpress.controller.web;

import com.uteexpress.dto.admin.CreateUserRequest;
import com.uteexpress.dto.admin.DashboardChartsResponse;
import com.uteexpress.dto.admin.HubFormRequest;
import com.uteexpress.dto.admin.ServiceTypeFormRequest;
import com.uteexpress.entity.Hub;
import com.uteexpress.entity.ServiceType;
import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.User;
import com.uteexpress.enums.HubType;
import com.uteexpress.enums.Role;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.exception.ResourceNotFoundException;
import com.uteexpress.repository.UserRepository;
import com.uteexpress.security.CustomUserDetails;
import com.uteexpress.service.DashboardService;
import com.uteexpress.service.HubService;
import com.uteexpress.service.RefundService;
import com.uteexpress.service.ServiceTypeService;
import com.uteexpress.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final DashboardService dashboardService;
    private final HubService hubService;
    private final ServiceTypeService serviceTypeService;
    private final UserService userService;
    private final RefundService refundService;
    private final UserRepository userRepository;

    @GetMapping({"", "/"})
    public String adminDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {
        DashboardChartsResponse summary = dashboardService.getDashboardCharts(from, to);
        model.addAttribute("summary", summary);
        model.addAttribute("fromDate", summary.fromDate());
        model.addAttribute("toDate", summary.toDate());
        return "admin/dashboard";
    }

    // ==================== HUB CRUD ====================
    @GetMapping("/hubs")
    public String listHubs(Model model) {
        model.addAttribute("hubs", hubService.findAll());
        model.addAttribute("hubTypes", HubType.values());
        if (!model.containsAttribute("hubForm")) {
            model.addAttribute("hubForm", new HubFormRequest());
        }
        return "admin/hubs";
    }

    @PostMapping("/hubs")
    public String saveHub(@Valid @ModelAttribute("hubForm") HubFormRequest form,
                          BindingResult bindingResult,
                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/admin/hubs";
        }
        Hub saved = hubService.saveOrUpdate(form);
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã lưu thông tin bưu cục " + saved.getCode() + " — " + saved.getName());
        return "redirect:/admin/hubs";
    }

    @PostMapping("/hubs/{id}/toggle-active")
    public String toggleHubActive(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Hub hub = hubService.toggleActive(id);
        String state = Boolean.TRUE.equals(hub.getActive()) ? "kích hoạt" : "tạm ngưng";
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã " + state + " bưu cục " + hub.getCode());
        return "redirect:/admin/hubs";
    }

    // ==================== SERVICE TYPE CRUD ====================
    @GetMapping("/services")
    public String listServices(Model model) {
        model.addAttribute("services", serviceTypeService.findAll());
        if (!model.containsAttribute("serviceForm")) {
            model.addAttribute("serviceForm", new ServiceTypeFormRequest());
        }
        return "admin/services";
    }

    @PostMapping("/services")
    public String saveService(@Valid @ModelAttribute("serviceForm") ServiceTypeFormRequest form,
                              BindingResult bindingResult,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/admin/services";
        }
        ServiceType saved = serviceTypeService.saveOrUpdate(form);
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã lưu gói dịch vụ " + saved.getCode() + " — " + saved.getName());
        return "redirect:/admin/services";
    }

    @PostMapping("/services/{id}/toggle-active")
    public String toggleServiceActive(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        ServiceType st = serviceTypeService.toggleActive(id);
        String state = Boolean.TRUE.equals(st.getActive()) ? "mở sử dụng" : "tạm ẩn";
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã " + state + " gói dịch vụ " + st.getName());
        return "redirect:/admin/services";
    }

    // ==================== USER MANAGEMENT ====================
    @GetMapping("/users")
    public String listUsers(@RequestParam(required = false) Role role,
                            @RequestParam(required = false) Long hubId,
                            @RequestParam(required = false) String keyword,
                            Model model) {
        model.addAttribute("users", userService.searchUsers(role, hubId, keyword));
        model.addAttribute("hubs", hubService.findActiveHubs());
        model.addAttribute("allHubs", hubService.findAll());
        model.addAttribute("roles", Role.values());
        model.addAttribute("selectedRole", role);
        model.addAttribute("selectedHubId", hubId);
        model.addAttribute("keyword", keyword);
        if (!model.containsAttribute("createUserForm")) {
            model.addAttribute("createUserForm", new CreateUserRequest());
        }
        return "admin/users";
    }

    @PostMapping("/users")
    public String createUser(@Valid @ModelAttribute("createUserForm") CreateUserRequest form,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/admin/users";
        }
        User created = userService.createStaffOrShipper(form);
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã tạo tài khoản " + created.getEmail() + " (" + created.getRole().getLabel() + ")");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/toggle-status")
    public String toggleUserStatus(@PathVariable Long id,
                                   @AuthenticationPrincipal CustomUserDetails currentAdmin,
                                   RedirectAttributes redirectAttributes) {
        User updated = userService.toggleStatus(id, currentAdmin.getId());
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã cập nhật trạng thái tài khoản " + updated.getEmail() + " → " + updated.getStatus().getLabel());
        return "redirect:/admin/users";
    }

    // ==================== VNPAY REFUNDS (BR-05 COMPLETION) ====================
    @GetMapping("/refunds")
    public String listVnPayRefunds(Model model) {
        java.util.List<Shipment> pendingRefunds = refundService.getPendingRefundShipments();
        java.util.List<Shipment> completedRefunds = refundService.getCompletedRefundShipments();
        java.math.BigDecimal pendingTotal = pendingRefunds.stream()
                .map(Shipment::getTotalFee)
                .filter(java.util.Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal refundedTotal = completedRefunds.stream()
                .map(Shipment::getTotalFee)
                .filter(java.util.Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        model.addAttribute("pendingRefunds", pendingRefunds);
        model.addAttribute("completedRefunds", completedRefunds);
        model.addAttribute("pendingTotal", pendingTotal);
        model.addAttribute("refundedTotal", refundedTotal);
        return "admin/refunds";
    }

    @PostMapping("/refunds/{id}/confirm")
    public String confirmVnPayRefund(@PathVariable Long id,
                                     @RequestParam(required = false) String refundRef,
                                     @AuthenticationPrincipal CustomUserDetails currentAdmin,
                                     RedirectAttributes redirectAttributes) {
        User adminActor = userRepository.findById(currentAdmin.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản quản trị hiện tại"));
        try {
            Shipment refunded = refundService.confirmVnPayRefund(id, refundRef, adminActor);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã xác nhận hoàn tiền cước VNPay cho vận đơn " + refunded.getTrackingCode() + ".");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/refunds";
    }
}
