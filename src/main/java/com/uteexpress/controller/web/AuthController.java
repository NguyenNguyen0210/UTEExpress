package com.uteexpress.controller.web;

import com.uteexpress.dto.auth.AuthTokenResponse;
import com.uteexpress.dto.auth.LoginRequest;
import com.uteexpress.dto.auth.RegisterRequest;
import com.uteexpress.enums.Role;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.security.CustomUserDetails;
import com.uteexpress.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/login")
    public String showLoginPage(@AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
        if (currentUser != null) {
            return "redirect:" + authService.resolveLandingPath(currentUser.getRole());
        }
        if (!model.containsAttribute("loginRequest")) {
            model.addAttribute("loginRequest", new LoginRequest());
        }
        return "auth/login";
    }

    @PostMapping("/login")
    public String handleLogin(@Valid @ModelAttribute("loginRequest") LoginRequest loginRequest,
                              BindingResult bindingResult,
                              HttpServletResponse response,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "auth/login";
        }
        try {
            AuthTokenResponse auth = authService.authenticate(loginRequest);
            response.addHeader(HttpHeaders.SET_COOKIE, authService.buildAuthCookie(auth.accessToken()).toString());
            redirectAttributes.addFlashAttribute("successMessage", "Đăng nhập thành công! Xin chào " + auth.fullName());
            return "redirect:" + authService.resolveLandingPath(Role.valueOf(auth.role()));
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "auth/login";
        }
    }

    @GetMapping("/register")
    public String showRegisterPage(@AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
        if (currentUser != null) {
            return "redirect:" + authService.resolveLandingPath(currentUser.getRole());
        }
        if (!model.containsAttribute("registerRequest")) {
            model.addAttribute("registerRequest", new RegisterRequest());
        }
        return "auth/register";
    }

    @PostMapping("/register")
    public String handleRegister(@Valid @ModelAttribute("registerRequest") RegisterRequest registerRequest,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }
        try {
            authService.registerCustomer(registerRequest);
            redirectAttributes.addFlashAttribute("successMessage", "Đăng ký tài khoản thành công! Vui lòng đăng nhập.");
            return "redirect:/auth/login";
        } catch (BusinessException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "auth/register";
        }
    }

    @PostMapping("/logout")
    public String handleLogout(HttpServletResponse response, RedirectAttributes redirectAttributes) {
        response.addHeader(HttpHeaders.SET_COOKIE, authService.buildLogoutCookie().toString());
        redirectAttributes.addFlashAttribute("successMessage", "Bạn đã đăng xuất khỏi hệ thống.");
        return "redirect:/auth/login";
    }
}
