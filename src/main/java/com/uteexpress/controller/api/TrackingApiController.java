package com.uteexpress.controller.api;

import com.uteexpress.dto.publicview.PublicTrackingResponse;
import com.uteexpress.service.PublicTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class TrackingApiController {

    private final PublicTrackingService publicTrackingService;

    @GetMapping("/tracking")
    public ResponseEntity<PublicTrackingResponse> trackShipments(
            @RequestParam(required = false) String codes) {
        return ResponseEntity.ok(publicTrackingService.lookupByCodes(codes));
    }
}
