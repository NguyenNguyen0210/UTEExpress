package com.uteexpress.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice(basePackages = "com.uteexpress.controller.web")
public class WebExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public String handleNotFound(ResourceNotFoundException ex, Model model, HttpServletResponse response) {
        response.setStatus(HttpStatus.NOT_FOUND.value());
        model.addAttribute("errorMessage", ex.getMessage());
        return "error/404";
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(AccessDeniedException ex, Model model, HttpServletResponse response) {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        model.addAttribute("errorMessage", ex.getMessage());
        return "error/403";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSize(MaxUploadSizeExceededException ex,
                                      HttpServletRequest request,
                                      RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", "Dung lượng file vượt quá giới hạn tối đa 5MB.");
        String safeReferer = resolveSafeReferer(request);
        return "redirect:" + (safeReferer != null ? safeReferer : "/");
    }

    @ExceptionHandler(BusinessException.class)
    public String handleBusiness(BusinessException ex,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes,
                                 Model model,
                                 HttpServletResponse response) {
        String safeReferer = resolveSafeReferer(request);
        if (safeReferer != null) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:" + safeReferer;
        }
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        model.addAttribute("errorMessage", ex.getMessage());
        return "error/500";
    }

    private String resolveSafeReferer(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return null;
        }
        if (referer.startsWith("/") && !referer.startsWith("//")) {
            return referer;
        }
        try {
            java.net.URI uri = java.net.URI.create(referer);
            if (uri.getHost() != null && uri.getHost().equalsIgnoreCase(request.getServerName())) {
                String path = uri.getRawPath() != null && !uri.getRawPath().isBlank() ? uri.getRawPath() : "/";
                return uri.getRawQuery() != null ? path + "?" + uri.getRawQuery() : path;
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }
}
