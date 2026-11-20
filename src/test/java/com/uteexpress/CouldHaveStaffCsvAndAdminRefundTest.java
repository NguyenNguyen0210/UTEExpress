package com.uteexpress;

import com.uteexpress.entity.Hub;
import com.uteexpress.entity.Notification;
import com.uteexpress.entity.ServiceType;
import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.User;
import com.uteexpress.enums.*;
import com.uteexpress.repository.HubRepository;
import com.uteexpress.repository.NotificationRepository;
import com.uteexpress.repository.ServiceTypeRepository;
import com.uteexpress.repository.ShipmentRepository;
import com.uteexpress.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CouldHaveStaffCsvAndAdminRefundTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private HubRepository hubRepository;

    @Autowired
    private ServiceTypeRepository serviceTypeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void staffCsvExportEnforcesHubScopeAndContainsUtf8Bom() throws Exception {
        String staffHnToken = loginAndExtractToken("staff.hn@uteexpress.vn", "123456");

        MvcResult res = mockMvc.perform(get("/staff/shipments/export")
                        .cookie(new Cookie("ACCESS_TOKEN", staffHnToken)))
                .andExpect(status().isOk())
                .andReturn();

        byte[] bytes = res.getResponse().getContentAsByteArray();
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);

        String csv = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        // Staff HN sees inter-province shipments destined to HN-CG01, but NOT intra-HCM shipments
        assertThat(csv).contains("UTE261120A8K4P2").contains("UTE261119B7M2Q9");
        assertThat(csv).doesNotContain("UTE261119C3N5X1");
    }

    @Test
    void adminConfirmsVnPayRefundTransitionsRefundPendingToRefunded() throws Exception {
        String adminToken = loginAndExtractToken("admin@uteexpress.vn", "123456");
        String staffToken = loginAndExtractToken("staff.hcm@uteexpress.vn", "123456");

        User shop = userRepository.findByEmailIgnoreCase("shop@uteexpress.vn").orElseThrow();
        Hub hcmHub = hubRepository.findByCodeIgnoreCase("HCM-TD01").orElseThrow();
        ServiceType express = serviceTypeRepository.findByCodeIgnoreCase("EXPRESS").orElseThrow();

        long beforeNotifIds = notificationRepository.findTop20ByUserIdOrderByCreatedAtDesc(shop.getId())
                .stream().mapToLong(Notification::getId).max().orElse(0L);

        String code = "UTERF" + String.valueOf(System.currentTimeMillis()).substring(7);
        Shipment refundShipment = shipmentRepository.save(Shipment.builder()
                .trackingCode(code)
                .customer(shop)
                .serviceType(express)
                .senderName("Shop Hoàng Nam")
                .senderPhone("0901234567")
                .senderProvince("TP. Hồ Chí Minh")
                .senderDistrict("TP. Thủ Đức")
                .senderAddress("01 Võ Văn Ngân")
                .receiverName("Khách Hoàn Tiền")
                .receiverPhone("0988000111")
                .receiverProvince("TP. Hồ Chí Minh")
                .receiverDistrict("TP. Thủ Đức")
                .receiverAddress("100 Kha Vạn Cân")
                .originHub(hcmHub)
                .destHub(hcmHub)
                .currentHub(hcmHub)
                .itemName("Hàng Test Hoàn Tiền")
                .actualWeightKg(new BigDecimal("1.00"))
                .chargeableWeightKg(new BigDecimal("1.00"))
                .isFragile(false)
                .inspectionPolicy(InspectionPolicy.ALLOW_VIEW_NO_TRY)
                .shippingFee(new BigDecimal("30000.00"))
                .codFee(BigDecimal.ZERO)
                .totalFee(new BigDecimal("30000.00"))
                .codAmount(BigDecimal.ZERO)
                .payerType(PayerType.SENDER)
                .paymentMethod(PaymentMethod.VNPAY)
                .paymentStatus(PaymentStatus.REFUND_PENDING)
                .status(ShipmentStatus.CANCELLED)
                .codStatus(CodStatus.NONE)
                .vnpTransactionNo("14777777")
                .build());

        try {
            // Non-admin blocked (403)
            mockMvc.perform(get("/admin/refunds").cookie(new Cookie("ACCESS_TOKEN", staffToken)))
                    .andExpect(status().isForbidden());

            // Admin views /admin/refunds cleanly
            mockMvc.perform(get("/admin/refunds").cookie(new Cookie("ACCESS_TOKEN", adminToken)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(code)));

            // Admin confirms refund
            mockMvc.perform(post("/admin/refunds/" + refundShipment.getId() + "/confirm")
                            .cookie(new Cookie("ACCESS_TOKEN", adminToken))
                            .param("refundRef", "VNP-REFUND-20261120"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/refunds"));

            Shipment updated = shipmentRepository.findById(refundShipment.getId()).orElseThrow();
            assertThat(updated.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        } finally {
            List<Notification> createdNotifs = notificationRepository.findTop20ByUserIdOrderByCreatedAtDesc(shop.getId())
                    .stream().filter(n -> n.getId() > beforeNotifIds).toList();
            if (!createdNotifs.isEmpty()) {
                notificationRepository.deleteAll(createdNotifs);
            }
            shipmentRepository.deleteById(refundShipment.getId());
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
}
