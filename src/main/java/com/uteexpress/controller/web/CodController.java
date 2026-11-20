package com.uteexpress.controller.web;

import com.uteexpress.dto.cod.CustomerPendingCodDto;
import com.uteexpress.dto.cod.SettlementDetailView;
import com.uteexpress.dto.cod.ShipperCodGroupDto;
import com.uteexpress.entity.CodSettlement;
import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.User;
import com.uteexpress.enums.SettlementStatus;
import com.uteexpress.exception.BusinessException;
import com.uteexpress.exception.ResourceNotFoundException;
import com.uteexpress.repository.UserRepository;
import com.uteexpress.security.CustomUserDetails;
import com.uteexpress.service.CodSettlementService;
import com.uteexpress.service.CsvExportService;
import com.uteexpress.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Controller
@RequiredArgsConstructor
public class CodController {

    private final CodSettlementService codSettlementService;
    private final ShipmentService shipmentService;
    private final UserRepository userRepository;
    private final CsvExportService csvExportService;

    // ==================== SHIPPER COD SUMMARY (US-7.1) ====================

    @GetMapping("/shipper/cod")
    public String shipperCodSummary(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        User shipper = getCurrentUser(userDetails);
        List<Shipment> heldShipments = codSettlementService.getShipperHeldShipments(shipper.getId());
        BigDecimal totalCashHeld = codSettlementService.calculateTotalCashHeldByShipper(heldShipments);
        BigDecimal totalCodAmount = codSettlementService.calculateTotalCodAmount(heldShipments);

        Map<Long, BigDecimal> receiverCollectMap = new HashMap<>();
        for (Shipment shipment : heldShipments) {
            if (shipment.getId() != null) {
                receiverCollectMap.put(shipment.getId(), shipmentService.calculateReceiverCollectAmount(shipment));
            }
        }

        model.addAttribute("shipper", shipper);
        model.addAttribute("heldShipments", heldShipments);
        model.addAttribute("totalCashHeld", totalCashHeld);
        model.addAttribute("totalCodAmount", totalCodAmount);
        model.addAttribute("receiverCollectMap", receiverCollectMap);
        return "shipper/cod-summary";
    }

    // ==================== STAFF COD COLLECTION (US-7.1) ====================

    @GetMapping("/staff/cod")
    public String staffCodCollection(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        User staffActor = getCurrentUser(userDetails);
        List<ShipperCodGroupDto> shipperGroups = codSettlementService.getHeldCodGroupedByShipper(staffActor);

        int totalShipmentCount = 0;
        BigDecimal totalCodAmount = BigDecimal.ZERO;
        BigDecimal totalCashToCollect = BigDecimal.ZERO;
        Map<Long, BigDecimal> receiverCollectMap = new HashMap<>();

        for (ShipperCodGroupDto group : shipperGroups) {
            totalShipmentCount += group.shipments().size();
            totalCodAmount = totalCodAmount.add(group.totalCodAmount());
            totalCashToCollect = totalCashToCollect.add(group.totalReceiverCollectAmount());
            for (Shipment shipment : group.shipments()) {
                if (shipment.getId() != null) {
                    receiverCollectMap.put(shipment.getId(), shipmentService.calculateReceiverCollectAmount(shipment));
                }
            }
        }

        model.addAttribute("actor", staffActor);
        model.addAttribute("shipperGroups", shipperGroups);
        model.addAttribute("totalShipmentCount", totalShipmentCount);
        model.addAttribute("totalCodAmount", totalCodAmount);
        model.addAttribute("totalCashToCollect", totalCashToCollect);
        model.addAttribute("receiverCollectMap", receiverCollectMap);
        return "staff/cod-collection";
    }

    @PostMapping("/staff/cod/collect")
    public String collectCodFromShipper(@RequestParam(name = "shipmentIds", required = false) List<Long> shipmentIds,
                                        @AuthenticationPrincipal CustomUserDetails userDetails,
                                        RedirectAttributes redirectAttributes) {
        User staffActor = getCurrentUser(userDetails);
        try {
            int count = codSettlementService.collectCodFromShipper(shipmentIds, staffActor);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã xác nhận thu tiền COD từ Shipper cho " + count + " vận đơn thành công.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/staff/cod";
    }

    // ==================== ADMIN COD SETTLEMENTS (BR-09, US-7.2) ====================

    @GetMapping("/admin/cod-settlements")
    public String adminCodSettlements(Model model) {
        List<CustomerPendingCodDto> readyCustomers = codSettlementService.getCustomersReadyForSettlement();
        List<SettlementDetailView> settlements = codSettlementService.getAllSettlements();

        BigDecimal readyNetTotal = readyCustomers.stream()
                .map(CustomerPendingCodDto::netAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pendingSettlementCount = settlements.stream()
                .filter(v -> v.settlement().getStatus() == SettlementStatus.PENDING)
                .count();

        BigDecimal pendingSettlementNetTotal = settlements.stream()
                .map(SettlementDetailView::settlement)
                .filter(s -> s.getStatus() == SettlementStatus.PENDING)
                .map(CodSettlement::getNetAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal settledNetTotal = settlements.stream()
                .map(SettlementDetailView::settlement)
                .filter(s -> s.getStatus() == SettlementStatus.SETTLED)
                .map(CodSettlement::getNetAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("readyCustomers", readyCustomers);
        model.addAttribute("settlements", settlements);
        model.addAttribute("readyCustomerCount", readyCustomers.size());
        model.addAttribute("readyNetTotal", readyNetTotal);
        model.addAttribute("pendingSettlementCount", pendingSettlementCount);
        model.addAttribute("pendingSettlementNetTotal", pendingSettlementNetTotal);
        model.addAttribute("settledNetTotal", settledNetTotal);
        return "admin/cod-settlements";
    }

    @PostMapping("/admin/cod-settlements")
    public String createCodSettlement(@RequestParam Long customerId,
                                      @RequestParam(required = false) String note,
                                      @AuthenticationPrincipal CustomUserDetails userDetails,
                                      RedirectAttributes redirectAttributes) {
        User adminActor = getCurrentUser(userDetails);
        try {
            CodSettlement created = codSettlementService.createSettlement(customerId, note, adminActor);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã tạo phiếu đối soát COD " + created.getSettlementCode()
                            + " cho " + created.getCustomer().getFullName()
                            + " (Thực chuyển: " + formatVnd(created.getNetAmount()) + " đ).");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/cod-settlements";
    }

    @PostMapping("/admin/cod-settlements/{id}/settle")
    public String markCodSettlementSettled(@PathVariable Long id,
                                           @RequestParam(required = false) String transactionRef,
                                           @AuthenticationPrincipal CustomUserDetails userDetails,
                                           RedirectAttributes redirectAttributes) {
        User adminActor = getCurrentUser(userDetails);
        try {
            CodSettlement settled = codSettlementService.markSettled(id, transactionRef, adminActor);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã xác nhận chuyển khoản phiếu " + settled.getSettlementCode()
                            + " (Mã GD: " + settled.getTransactionRef() + ") và gửi thông báo tới khách hàng.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/cod-settlements";
    }

    @GetMapping("/admin/cod-settlements/export")
    public ResponseEntity<byte[]> exportAdminCodSettlementsCsv() {
        List<SettlementDetailView> settlements = codSettlementService.getAllSettlements();
        byte[] csvBytes = csvExportService.exportCodSettlementsCsv(settlements);
        String filename = "UTEExpress-DoiSoat-COD-Admin-" + java.time.LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentLength(csvBytes.length)
                .body(csvBytes);
    }

    // ==================== CUSTOMER COD HISTORY (US-7.2) ====================

    @GetMapping("/customer/cod")
    public String customerCodHistory(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        User customer = getCurrentUser(userDetails);
        List<SettlementDetailView> settlements = codSettlementService.getCustomerSettlements(customer.getId());
        List<Shipment> pendingCodShipments = codSettlementService.getCustomerPendingCodShipments(customer.getId());

        BigDecimal pendingCodTotal = codSettlementService.calculateTotalCodAmount(pendingCodShipments);

        BigDecimal pendingTransferTotal = settlements.stream()
                .map(SettlementDetailView::settlement)
                .filter(s -> s.getStatus() == SettlementStatus.PENDING)
                .map(CodSettlement::getNetAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal settledNetTotal = settlements.stream()
                .map(SettlementDetailView::settlement)
                .filter(s -> s.getStatus() == SettlementStatus.SETTLED)
                .map(CodSettlement::getNetAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("customer", customer);
        model.addAttribute("settlements", settlements);
        model.addAttribute("pendingCodShipments", pendingCodShipments);
        model.addAttribute("pendingCodTotal", pendingCodTotal);
        model.addAttribute("pendingTransferTotal", pendingTransferTotal);
        model.addAttribute("settledNetTotal", settledNetTotal);
        return "customer/cod-list";
    }

    @GetMapping("/customer/cod/export")
    public ResponseEntity<byte[]> exportCustomerCodCsv(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User customer = getCurrentUser(userDetails);
        List<SettlementDetailView> settlements = codSettlementService.getCustomerSettlements(customer.getId());
        byte[] csvBytes = csvExportService.exportCodSettlementsCsv(settlements);
        String filename = "UTEExpress-DoiSoat-COD-" + java.time.LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentLength(csvBytes.length)
                .body(csvBytes);
    }

    private User getCurrentUser(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getId() == null) {
            throw new AccessDeniedException("Phiên đăng nhập không hợp lệ");
        }
        return userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản hiện tại"));
    }

    private String formatVnd(BigDecimal amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        DecimalFormat formatter = new DecimalFormat("#,##0", symbols);
        return formatter.format(amount != null ? amount : BigDecimal.ZERO);
    }
}
