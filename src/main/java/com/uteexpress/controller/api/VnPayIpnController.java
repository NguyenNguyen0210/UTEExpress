package com.uteexpress.controller.api;

import com.uteexpress.dto.payment.VnPayProcessResult;
import com.uteexpress.service.VnPayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class VnPayIpnController {

    private final VnPayService vnPayService;

    @GetMapping("/vnpay-ipn")
    public ResponseEntity<Map<String, String>> vnpayIpn(@RequestParam Map<String, String> params) {
        VnPayProcessResult result = vnPayService.processCallback(params);
        Map<String, String> response = new LinkedHashMap<>();
        response.put("RspCode", result.rspCode());
        response.put("Message", result.message());
        return ResponseEntity.ok(response);
    }
}
