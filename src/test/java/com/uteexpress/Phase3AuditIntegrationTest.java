package com.uteexpress;

import com.uteexpress.config.VnPayConfig;
import com.uteexpress.entity.CodSettlement;
import com.uteexpress.entity.Hub;
import com.uteexpress.entity.Notification;
import com.uteexpress.entity.ServiceType;
import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.User;
import com.uteexpress.enums.*;
import com.uteexpress.repository.CodSettlementRepository;
import com.uteexpress.repository.HubRepository;
import com.uteexpress.repository.NotificationRepository;
import com.uteexpress.repository.ServiceTypeRepository;
import com.uteexpress.repository.ShipmentRepository;
import com.uteexpress.repository.UserRepository;
import com.uteexpress.service.VnPayService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class Phase3AuditIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private CodSettlementRepository codSettlementRepository;

    @Autowired
    private ServiceTypeRepository serviceTypeRepository;

    @Autowired
    private HubRepository hubRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private VnPayService vnPayService;

    @Autowired
    private VnPayConfig vnPayConfig;

    @Test
    void auditAllPhase3PagesRenderCleanlyAndEnforceSecurityMatrix() throws Exception {
        String adminToken = loginAndExtractToken("admin@uteexpress.vn", "123456");
        String staffHcmToken = loginAndExtractToken("staff.hcm@uteexpress.vn", "123456");
        String shipperHcmToken = loginAndExtractToken("shipper.hcm@uteexpress.vn", "123456");
        String customerToken = loginAndExtractToken("shop@uteexpress.vn", "123456");

        // 1. Admin Dashboard page (including mobile Offcanvas sidebar) & Charts API
        mockMvc.perform(get("/admin").cookie(new Cookie("ACCESS_TOKEN", adminToken)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("mobilePortalSidebar")))
                .andExpect(content().string(containsString("dailyRevenueChart")))
                .andExpect(content().string(containsString("statusDoughnutChart")))
                .andExpect(content().string(containsString("hubBarChart")));

        mockMvc.perform(get("/api/v1/admin/dashboard/charts").cookie(new Cookie("ACCESS_TOKEN", adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalShipments").isNumber())
                .andExpect(jsonPath("$.dailyMetrics").isArray())
                .andExpect(jsonPath("$.hubPerformance").isArray());

        // Security matrix: Guest -> 401 JSON, Customer -> 403 JSON on admin charts API
        mockMvc.perform(get("/api/v1/admin/dashboard/charts").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(get("/api/v1/admin/dashboard/charts").cookie(new Cookie("ACCESS_TOKEN", customerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // 2. All 4 COD Portal Pages render HTTP 200 without Thymeleaf errors
        mockMvc.perform(get("/shipper/cod").cookie(new Cookie("ACCESS_TOKEN", shipperHcmToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/staff/cod").cookie(new Cookie("ACCESS_TOKEN", staffHcmToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/cod-settlements").cookie(new Cookie("ACCESS_TOKEN", adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/customer/cod").cookie(new Cookie("ACCESS_TOKEN", customerToken)))
                .andExpect(status().isOk());

        // 3. Waybill PDF A6 & A5 + Anti-IDOR (Shipper must get 403)
        Shipment sample = shipmentRepository.findByTrackingCodeIgnoreCase("UTE261120A8K4P2").orElseThrow();
        MvcResult pdfA6 = mockMvc.perform(get("/shipments/" + sample.getId() + "/waybill")
                        .cookie(new Cookie("ACCESS_TOKEN", customerToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andReturn();
        assertThat(new String(pdfA6.getResponse().getContentAsByteArray(), 0, 5)).isEqualTo("%PDF-");

        MvcResult pdfA5 = mockMvc.perform(get("/shipments/" + sample.getId() + "/waybill?size=A5")
                        .cookie(new Cookie("ACCESS_TOKEN", staffHcmToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andReturn();
        assertThat(new String(pdfA5.getResponse().getContentAsByteArray(), 0, 5)).isEqualTo("%PDF-");

        mockMvc.perform(get("/shipments/" + sample.getId() + "/waybill")
                        .cookie(new Cookie("ACCESS_TOKEN", shipperHcmToken)))
                .andExpect(status().isForbidden());

        // 4. Chat CSKH API + Anti-IDOR (Customer owner & Hub Staff OK; Shipper 403 JSON)
        mockMvc.perform(get("/api/v1/chat/" + sample.getId() + "/messages")
                        .cookie(new Cookie("ACCESS_TOKEN", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").isString());

        mockMvc.perform(get("/api/v1/chat/" + sample.getId() + "/messages")
                        .cookie(new Cookie("ACCESS_TOKEN", staffHcmToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/chat/" + sample.getId() + "/messages")
                        .cookie(new Cookie("ACCESS_TOKEN", shipperHcmToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // 5. Protected POD File Endpoint (no POD image on sample -> 404 for owner, 403 for unassigned Shipper)
        mockMvc.perform(get("/files/pod/" + sample.getId())
                        .cookie(new Cookie("ACCESS_TOKEN", customerToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/files/pod/" + sample.getId())
                        .cookie(new Cookie("ACCESS_TOKEN", shipperHcmToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditVnPayEndToEndCreateRedirectReturnAndIdempotentIpn() throws Exception {
        String customerToken = loginAndExtractToken("shop@uteexpress.vn", "123456");
        ServiceType express = serviceTypeRepository.findByCodeIgnoreCase("EXPRESS").orElseThrow();

        // 1. Create SENDER + VNPAY shipment -> should redirect directly to VNPay Sandbox payUrl
        MvcResult createRes = mockMvc.perform(post("/customer/shipments")
                        .cookie(new Cookie("ACCESS_TOKEN", customerToken))
                        .param("senderName", "Shop Hoàng Nam VNPay")
                        .param("senderPhone", "0901234567")
                        .param("senderProvince", "TP. Hồ Chí Minh")
                        .param("senderDistrict", "TP. Thủ Đức")
                        .param("senderAddress", "01 Võ Văn Ngân")
                        .param("receiverName", "Khách VNPay")
                        .param("receiverPhone", "0981112222")
                        .param("receiverProvince", "Hà Nội")
                        .param("receiverDistrict", "Quận Cầu Giấy")
                        .param("receiverAddress", "100 Xuân Thủy")
                        .param("itemName", "Bàn phím Test VNPay")
                        .param("actualWeightKg", "1.0")
                        .param("inspectionPolicy", "ALLOW_VIEW_NO_TRY")
                        .param("serviceTypeId", String.valueOf(express.getId()))
                        .param("codAmount", "0")
                        .param("payerType", "SENDER")
                        .param("paymentMethod", "VNPAY"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String payRedirect = createRes.getResponse().getRedirectedUrl();
        assertThat(payRedirect).isNotNull().startsWith(vnPayConfig.getPayUrl()).contains("vnp_SecureHash=");

        String txnRef = extractQueryParam(payRedirect, "vnp_TxnRef");
        Shipment created = shipmentRepository.findByVnpTxnRef(txnRef).orElseThrow();
        assertThat(created.getStatus()).isEqualTo(ShipmentStatus.PENDING_PAYMENT);
        assertThat(created.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);

        try {
            // 2. Tampered IPN signature (TC-VNPAY-04) -> RspCode = 97
            mockMvc.perform(get("/api/v1/payment/vnpay-ipn")
                            .param("vnp_TxnRef", txnRef)
                            .param("vnp_Amount", "100")
                            .param("vnp_ResponseCode", "00")
                            .param("vnp_SecureHash", "tampered_hash"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.RspCode").value("97"));

            // 3. Valid Return URL callback -> payment-result.html renders 200, status becomes CREATED + PAID
            Map<String, String> cbParams = new LinkedHashMap<>();
            cbParams.put("vnp_TmnCode", vnPayConfig.getTmnCode());
            cbParams.put("vnp_Amount", created.getTotalFee()
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .toPlainString());
            cbParams.put("vnp_BankCode", "NCB");
            cbParams.put("vnp_OrderInfo", "Thanh toan cuoc van don " + created.getTrackingCode());
            cbParams.put("vnp_ResponseCode", "00");
            cbParams.put("vnp_TransactionStatus", "00");
            cbParams.put("vnp_TransactionNo", "14999999");
            cbParams.put("vnp_TxnRef", txnRef);

            String signedQuery = vnPayService.buildSignedQuery(cbParams, vnPayConfig.getHashSecret());
            String secureHash = extractQueryParam("?" + signedQuery, "vnp_SecureHash");

            MockHttpServletRequestBuilder returnReq = get("/payment/vnpay-return");
            cbParams.forEach(returnReq::param);
            returnReq.param("vnp_SecureHash", secureHash);

            mockMvc.perform(returnReq)
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(created.getTrackingCode())));

            Shipment paid = shipmentRepository.findById(created.getId()).orElseThrow();
            assertThat(paid.getStatus()).isEqualTo(ShipmentStatus.CREATED);
            assertThat(paid.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(paid.getVnpTransactionNo()).isEqualTo("14999999");

            // 4. Duplicate IPN call (TC-VNPAY-03 idempotent) -> RspCode = 02
            MockHttpServletRequestBuilder ipnReq = get("/api/v1/payment/vnpay-ipn");
            cbParams.forEach(ipnReq::param);
            ipnReq.param("vnp_SecureHash", secureHash);

            mockMvc.perform(ipnReq)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.RspCode").value("02"));
        } finally {
            shipmentRepository.deleteById(created.getId());
        }
    }

    @Test
    void auditFullCodSettlementFlowWithFeeDeductionNoDoubleCountAndNotificationRead() throws Exception {
        String adminToken = loginAndExtractToken("admin@uteexpress.vn", "123456");
        String staffHcmToken = loginAndExtractToken("staff.hcm@uteexpress.vn", "123456");

        Hub hcmHub = hubRepository.findByCodeIgnoreCase("HCM-TD01").orElseThrow();
        ServiceType standard = serviceTypeRepository.findByCodeIgnoreCase("STANDARD").orElseThrow();
        User shipperHcm = userRepository.findByEmailIgnoreCase("shipper.hcm@uteexpress.vn").orElseThrow();

        String uniqueEmail = "audit.cod." + System.currentTimeMillis() + "@uteexpress.vn";
        User tempCustomer = userRepository.save(User.builder()
                .email(uniqueEmail)
                .phone("0999" + String.valueOf(System.currentTimeMillis()).substring(7))
                .passwordHash(passwordEncoder.encode("123456"))
                .fullName("Shop Audit COD")
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .bankName("Vietcombank")
                .bankAccountNumber("9988776655")
                .bankAccountHolder("SHOP AUDIT COD")
                .build());

        String trackingCode = "UTEAUDITCOD" + String.valueOf(System.currentTimeMillis()).substring(8);
        Shipment codShipment = shipmentRepository.save(Shipment.builder()
                .trackingCode(trackingCode)
                .customer(tempCustomer)
                .serviceType(standard)
                .senderName("Shop Audit COD")
                .senderPhone(tempCustomer.getPhone())
                .senderProvince("TP. Hồ Chí Minh")
                .senderDistrict("TP. Thủ Đức")
                .senderAddress("01 Võ Văn Ngân")
                .receiverName("Người Nhận Audit")
                .receiverPhone("0911223344")
                .receiverProvince("TP. Hồ Chí Minh")
                .receiverDistrict("TP. Thủ Đức")
                .receiverAddress("99 Kha Vạn Cân")
                .originHub(hcmHub)
                .destHub(hcmHub)
                .currentHub(hcmHub)
                .pickupShipper(shipperHcm)
                .deliveryShipper(shipperHcm)
                .itemName("Hàng Audit COD")
                .actualWeightKg(new BigDecimal("1.00"))
                .chargeableWeightKg(new BigDecimal("1.00"))
                .isFragile(false)
                .inspectionPolicy(InspectionPolicy.ALLOW_VIEW_NO_TRY)
                .shippingFee(new BigDecimal("25000.00"))
                .codFee(new BigDecimal("5000.00"))
                .totalFee(new BigDecimal("30000.00"))
                .codAmount(new BigDecimal("500000.00"))
                .payerType(PayerType.SENDER)
                .paymentMethod(PaymentMethod.CASH)
                .paymentStatus(PaymentStatus.UNPAID)
                .status(ShipmentStatus.DELIVERED)
                .codStatus(CodStatus.HELD_BY_SHIPPER)
                .deliveredAt(OffsetDateTime.now().minusHours(2))
                .build());

        Long createdSettlementId = null;
        try {
            String tempCustomerToken = loginAndExtractToken(uniqueEmail, "123456");

            // Step 1: Before settlement, /customer/cod shows 1 pending shipment (500,000đ) and 0đ pending transfer
            MvcResult beforeRes = mockMvc.perform(get("/customer/cod")
                            .cookie(new Cookie("ACCESS_TOKEN", tempCustomerToken)))
                    .andExpect(status().isOk())
                    .andReturn();
            @SuppressWarnings("unchecked")
            List<Shipment> pendingBefore = (List<Shipment>) beforeRes.getModelAndView().getModel().get("pendingCodShipments");
            assertThat(pendingBefore).hasSize(1);
            assertThat((BigDecimal) beforeRes.getModelAndView().getModel().get("pendingCodTotal"))
                    .isEqualByComparingTo("500000.00");

            // Step 2: Staff HCM collects COD cash from Shipper (HELD_BY_SHIPPER -> COLLECTED_BY_HUB)
            mockMvc.perform(post("/staff/cod/collect")
                            .cookie(new Cookie("ACCESS_TOKEN", staffHcmToken))
                            .param("shipmentIds", String.valueOf(codShipment.getId())))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/staff/cod"));

            Shipment collected = shipmentRepository.findById(codShipment.getId()).orElseThrow();
            assertThat(collected.getCodStatus()).isEqualTo(CodStatus.COLLECTED_BY_HUB);

            // Step 3: Admin creates COD settlement (BR-09: netAmount = 500,000 - 30,000 = 470,000)
            mockMvc.perform(post("/admin/cod-settlements")
                            .cookie(new Cookie("ACCESS_TOKEN", adminToken))
                            .param("customerId", String.valueOf(tempCustomer.getId()))
                            .param("note", "Đối soát Audit Phase 3"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/cod-settlements"));

            List<CodSettlement> settlements = codSettlementRepository.findByCustomerIdOrderByCreatedAtDesc(tempCustomer.getId());
            assertThat(settlements).hasSize(1);
            CodSettlement settlement = settlements.get(0);
            createdSettlementId = settlement.getId();
            assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PENDING);
            assertThat(settlement.getTotalCodAmount()).isEqualByComparingTo("500000.00");
            assertThat(settlement.getTotalShippingDeduction()).isEqualByComparingTo("30000.00");
            assertThat(settlement.getNetAmount()).isEqualByComparingTo("470000.00");

            // Step 4: Verify audit fix — /customer/cod must NOT double-count shipments already in PENDING settlement
            MvcResult pendingRes = mockMvc.perform(get("/customer/cod")
                            .cookie(new Cookie("ACCESS_TOKEN", tempCustomerToken)))
                    .andExpect(status().isOk())
                    .andReturn();
            @SuppressWarnings("unchecked")
            List<Shipment> pendingAfterCreate = (List<Shipment>) pendingRes.getModelAndView().getModel().get("pendingCodShipments");
            assertThat(pendingAfterCreate).isEmpty();
            assertThat((BigDecimal) pendingRes.getModelAndView().getModel().get("pendingCodTotal"))
                    .isEqualByComparingTo("0.00");
            assertThat((BigDecimal) pendingRes.getModelAndView().getModel().get("pendingTransferTotal"))
                    .isEqualByComparingTo("470000.00");

            // Step 5: Admin marks settlement as SETTLED -> updates shipment codStatus=SETTLED, paymentStatus=PAID, sends notification
            mockMvc.perform(post("/admin/cod-settlements/" + createdSettlementId + "/settle")
                            .cookie(new Cookie("ACCESS_TOKEN", adminToken))
                            .param("transactionRef", "VCB-AUDIT-20261120"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/cod-settlements"));

            CodSettlement settled = codSettlementRepository.findById(createdSettlementId).orElseThrow();
            assertThat(settled.getStatus()).isEqualTo(SettlementStatus.SETTLED);
            assertThat(settled.getTransactionRef()).isEqualTo("VCB-AUDIT-20261120");

            Shipment settledShipment = shipmentRepository.findById(codShipment.getId()).orElseThrow();
            assertThat(settledShipment.getCodStatus()).isEqualTo(CodStatus.SETTLED);
            assertThat(settledShipment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

            // Step 6: Verify customer received notification & mark-as-read API works (US-6.1 AC #2)
            List<Notification> notifs = notificationRepository.findTop20ByUserIdOrderByCreatedAtDesc(tempCustomer.getId());
            assertThat(notifs).isNotEmpty();
            Long notifId = notifs.get(0).getId();
            assertThat(notificationRepository.countByUserIdAndIsReadFalse(tempCustomer.getId())).isEqualTo(1L);

            mockMvc.perform(post("/api/v1/notifications/" + notifId + "/read")
                            .cookie(new Cookie("ACCESS_TOKEN", tempCustomerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            assertThat(notificationRepository.countByUserIdAndIsReadFalse(tempCustomer.getId())).isZero();
        } finally {
            List<Notification> cleanupNotifs = notificationRepository.findTop20ByUserIdOrderByCreatedAtDesc(tempCustomer.getId());
            if (!cleanupNotifs.isEmpty()) {
                notificationRepository.deleteAll(cleanupNotifs);
            }
            shipmentRepository.findById(codShipment.getId()).ifPresent(s -> {
                s.setSettlement(null);
                shipmentRepository.save(s);
                shipmentRepository.deleteById(s.getId());
            });
            if (createdSettlementId != null) {
                codSettlementRepository.deleteById(createdSettlementId);
            }
            userRepository.deleteById(tempCustomer.getId());
        }
    }

    private String loginAndExtractToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .param("email", email)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        int start = setCookie.indexOf("ACCESS_TOKEN=") + "ACCESS_TOKEN=".length();
        int end = setCookie.indexOf(';', start);
        return end > 0 ? setCookie.substring(start, end) : setCookie.substring(start);
    }

    private String extractQueryParam(String url, String key) {
        int q = url.indexOf('?');
        if (q < 0) return "";
        for (String pair : url.substring(q + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(key)) {
                return pair.substring(eq + 1);
            }
        }
        return "";
    }
}
