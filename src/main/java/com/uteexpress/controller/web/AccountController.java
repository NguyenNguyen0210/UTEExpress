package com.uteexpress.controller.web;

import com.uteexpress.entity.User;
import com.uteexpress.exception.ResourceNotFoundException;
import com.uteexpress.repository.UserRepository;
import com.uteexpress.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/password")
    public String showChangePasswordPage(@AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
        User user = resolveCurrentUser(currentUser);
        model.addAttribute("accountUser", user);
        return "account/change-password";
    }

    @PostMapping("/password")
    public String handleChangePassword(@RequestParam(required = false) String currentPassword,
                                       @RequestParam(required = false) String newPassword,
                                       @RequestParam(required = false) String confirmPassword,
                                       @AuthenticationPrincipal CustomUserDetails currentUser,
                                       RedirectAttributes redirectAttributes) {
        User user = resolveCurrentUser(currentUser);

        if (currentPassword == null || currentPassword.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng nhập mật khẩu hiện tại");
            return "redirect:/account/password";
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Mật khẩu hiện tại không chính xác");
            return "redirect:/account/password";
        }
        if (newPassword == null || newPassword.length() < 6) {
            redirectAttributes.addFlashAttribute("errorMessage", "Mật khẩu mới phải có ít nhất 6 ký tự");
            return "redirect:/account/password";
        }
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Xác nhận mật khẩu mới không khớp");
            return "redirect:/account/password";
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Mật khẩu mới không được trùng với mật khẩu hiện tại");
            return "redirect:/account/password";
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("successMessage", "Đổi mật khẩu tài khoản thành công!");
        return "redirect:/account/password";
    }

    private User resolveCurrentUser(CustomUserDetails currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new AccessDeniedException("Phiên đăng nhập không hợp lệ");
        }
        return userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản hiện tại"));
    }
}
