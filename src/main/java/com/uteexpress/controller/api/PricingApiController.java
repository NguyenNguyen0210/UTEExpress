package com.uteexpress.controller.api;

import com.uteexpress.dto.pricing.EstimateRequest;
import com.uteexpress.dto.pricing.EstimateResponse;
import com.uteexpress.service.PricingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PricingApiController {

    private final PricingService pricingService;

    @PostMapping("/estimate")
    public ResponseEntity<EstimateResponse> estimate(@Valid @RequestBody EstimateRequest request) {
        return ResponseEntity.ok(pricingService.estimate(request));
    }
}
