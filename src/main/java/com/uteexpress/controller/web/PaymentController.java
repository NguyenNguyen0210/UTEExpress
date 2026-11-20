package com.uteexpress.controller.web;

import com.uteexpress.dto.payment.VnPayProcessResult;
import com.uteexpress.entity.Shipment;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.security.CustomUserDetails;
import com.uteexpress.service.CustomerShipmentService;
import com.uteexpress.service.VnPayService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class PaymentController {

    private final VnPayService vnPayService;
    private final CustomerShipmentService customerShipmentService;

    @GetMapping("/payment/vnpay-return")
    public String vnpayReturn(@RequestParam Map<String, String> params, Model model) {
        VnPayProcessResult result = vnPayService.processCallback(params);
        model.addAttribute("result", result);
        model.addAttribute("shipment", result.shipment());
        model.addAttribute("vnpTxnRef", params.get("vnp_TxnRef"));
        model.addAttribute("vnpTransactionNo", params.get("vnp_TransactionNo"));
        model.addAttribute("vnpBankCode", params.get("vnp_BankCode"));
        model.addAttribute("vnpResponseCode", params.get("vnp_ResponseCode"));
        return "customer/payment-result";
    }

    @PostMapping("/customer/shipments/{id}/retry-vnpay")
    public String retryVnPay(@PathVariable Long id,
                             @AuthenticationPrincipal CustomUserDetails currentUser,
                             HttpServletRequest request,
                             RedirectAttributes redirectAttributes) {
        Shipment shipment = customerShipmentService.getCustomerShipmentDetail(currentUser.getId(), id);
        try {
            String payUrl = vnPayService.createPaymentUrl(shipment, resolveClientIp(request));
            return "redirect:" + payUrl;
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/customer/shipments/" + id;
        }
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
