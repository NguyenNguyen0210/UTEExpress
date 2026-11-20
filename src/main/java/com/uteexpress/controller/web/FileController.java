package com.uteexpress.controller.web;

import com.uteexpress.dto.media.PodFileResource;
import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.User;
import com.uteexpress.exception.ResourceNotFoundException;
import com.uteexpress.repository.UserRepository;
import com.uteexpress.security.CustomUserDetails;
import com.uteexpress.service.R2StorageService;
import com.uteexpress.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class FileController {

    private final ShipmentService shipmentService;
    private final R2StorageService r2StorageService;
    private final UserRepository userRepository;

    @GetMapping("/files/pod/{shipmentId}")
    public ResponseEntity<byte[]> streamPodImage(@PathVariable Long shipmentId,
                                                 @AuthenticationPrincipal CustomUserDetails currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new AccessDeniedException("Phiên đăng nhập không hợp lệ");
        }

        User actor = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản hiện tại"));
        Shipment shipment = shipmentService.getShipmentById(shipmentId);

        // Strict Anti-IDOR check (08-TEST-PLAN.md Section 2 & TC-AUTH-03)
        shipmentService.assertCanViewShipment(shipment, actor);

        if (shipment.getPodImageKey() == null || shipment.getPodImageKey().isBlank()) {
            throw new ResourceNotFoundException("Vận đơn này chưa có ảnh bằng chứng giao hàng (POD)");
        }

        PodFileResource resource = r2StorageService.downloadPodImage(shipment.getPodImageKey());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(resource.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(resource.bytes());
    }
}
