package com.uteexpress.controller.web;

import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.User;
import com.uteexpress.enums.Role;
import com.uteexpress.exception.ResourceNotFoundException;
import com.uteexpress.repository.UserRepository;
import com.uteexpress.security.CustomUserDetails;
import com.uteexpress.service.ShipmentService;
import com.uteexpress.service.WaybillPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class WaybillController {

    private final WaybillPdfService waybillPdfService;
    private final ShipmentService shipmentService;
    private final UserRepository userRepository;

    @GetMapping("/shipments/{id}/waybill")
    public ResponseEntity<byte[]> downloadWaybill(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "A6") String size,
                                                  @AuthenticationPrincipal CustomUserDetails currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new AccessDeniedException("Vui lòng đăng nhập để tải tem vận đơn PDF");
        }
        if (currentUser.getRole() == Role.SHIPPER) {
            throw new AccessDeniedException("Shipper không có quyền tải tem vận đơn PDF");
        }

        User actor = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản hiện tại"));
        if (actor.getRole() == Role.SHIPPER) {
            throw new AccessDeniedException("Shipper không có quyền tải tem vận đơn PDF");
        }

        Shipment shipment = shipmentService.getShipmentById(id);
        shipmentService.assertCanViewShipment(shipment, actor);

        String normalizedSize = "A5".equalsIgnoreCase(size != null ? size.trim() : "") ? "A5" : "A6";
        byte[] pdfBytes = waybillPdfService.generateWaybillPdf(shipment, normalizedSize);
        String filename = "UTEExpress-" + shipment.getTrackingCode() + "-" + normalizedSize + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentLength(pdfBytes.length)
                .body(pdfBytes);
    }
}
