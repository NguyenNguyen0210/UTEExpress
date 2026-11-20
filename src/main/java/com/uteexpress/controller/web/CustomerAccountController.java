package com.uteexpress.controller.web;

import com.uteexpress.dto.customer.AddressFormRequest;
import com.uteexpress.dto.customer.ProfileUpdateRequest;
import com.uteexpress.entity.CustomerAddress;
import com.uteexpress.entity.User;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.security.CustomUserDetails;
import com.uteexpress.service.CustomerAddressService;
import com.uteexpress.service.CustomerProfileService;
import com.uteexpress.service.HubService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/customer")
@RequiredArgsConstructor
public class CustomerAccountController {

    private final CustomerAddressService customerAddressService;
    private final CustomerProfileService customerProfileService;
    private final HubService hubService;

    // ==================== ADDRESS BOOK ====================

    @GetMapping("/addresses")
    public String listAddresses(@AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
        model.addAttribute("addresses", customerAddressService.listAddresses(currentUser.getId()));
        model.addAttribute("provinces", hubService.getActiveProvinces());
        if (!model.containsAttribute("addressForm")) {
            model.addAttribute("addressForm", new AddressFormRequest());
        }
        return "customer/addresses";
    }

    @PostMapping("/addresses")
    public String addAddress(@Valid @ModelAttribute("addressForm") AddressFormRequest form,
                             BindingResult bindingResult,
                             @AuthenticationPrincipal CustomUserDetails currentUser,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            redirectAttributes.addFlashAttribute("addressForm", form);
            return "redirect:/customer/addresses";
        }
        try {
            CustomerAddress saved = customerAddressService.addAddress(currentUser.getId(), form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã thêm địa chỉ '" + saved.getLabel() + "' vào sổ địa chỉ.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            redirectAttributes.addFlashAttribute("addressForm", form);
        }
        return "redirect:/customer/addresses";
    }

    @PostMapping("/addresses/{id}/default")
    public String setDefaultAddress(@PathVariable Long id,
                                    @AuthenticationPrincipal CustomUserDetails currentUser,
                                    RedirectAttributes redirectAttributes) {
        customerAddressService.setDefaultAddress(currentUser.getId(), id);
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã đặt làm địa chỉ lấy hàng mặc định.");
        return "redirect:/customer/addresses";
    }

    @PostMapping("/addresses/{id}/delete")
    public String deleteAddress(@PathVariable Long id,
                                @AuthenticationPrincipal CustomUserDetails currentUser,
                                RedirectAttributes redirectAttributes) {
        customerAddressService.deleteAddress(currentUser.getId(), id);
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã xóa địa chỉ khỏi sổ địa chỉ.");
        return "redirect:/customer/addresses";
    }

    // ==================== PROFILE & COD BANK ====================

    @GetMapping("/profile")
    public String viewProfile(@AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
        User user = customerProfileService.getProfile(currentUser.getId());
        model.addAttribute("user", user);
        model.addAttribute("provinces", hubService.getActiveProvinces());
        if (!model.containsAttribute("profileForm")) {
            model.addAttribute("profileForm", ProfileUpdateRequest.from(user));
        }
        return "customer/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@Valid @ModelAttribute("profileForm") ProfileUpdateRequest form,
                                BindingResult bindingResult,
                                @AuthenticationPrincipal CustomUserDetails currentUser,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            redirectAttributes.addFlashAttribute("profileForm", form);
            return "redirect:/customer/profile";
        }
        try {
            customerProfileService.updateProfile(currentUser.getId(), form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật hồ sơ cá nhân và tài khoản ngân hàng nhận COD thành công.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            redirectAttributes.addFlashAttribute("profileForm", form);
        }
        return "redirect:/customer/profile";
    }
}
