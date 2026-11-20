package com.uteexpress;

import com.uteexpress.entity.ServiceType;
import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.User;
import com.uteexpress.enums.CodStatus;
import com.uteexpress.enums.ShipmentStatus;
import com.uteexpress.repository.NotificationRepository;
import com.uteexpress.repository.ServiceTypeRepository;
import com.uteexpress.repository.ShipmentRepository;
import com.uteexpress.repository.UserRepository;
import com.uteexpress.service.ShipmentService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class Phase2InterProvinceE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private ServiceTypeRepository serviceTypeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ShipmentService shipmentService;

    @Test
    void fullInterProvinceFlowAcrossAllRolesAndAntiIdorChecks() throws Exception {
        String customerToken = loginAndExtractToken("shop@uteexpress.vn", "123456");
        String staffHcmToken = loginAndExtractToken("staff.hcm@uteexpress.vn", "123456");
        String staffHnToken = loginAndExtractToken("staff.hn@uteexpress.vn", "123456");
        String shipperHcmToken = loginAndExtractToken("shipper.hcm@uteexpress.vn", "123456");
        String shipperHnToken = loginAndExtractToken("shipper.hn@uteexpress.vn", "123456");

        ServiceType express = serviceTypeRepository.findByCodeIgnoreCase("EXPRESS").orElseThrow();
        User shipperHcm = userRepository.findByEmailIgnoreCase("shipper.hcm@uteexpress.vn").orElseThrow();
        User shipperHn = userRepository.findByEmailIgnoreCase("shipper.hn@uteexpress.vn").orElseThrow();
        User staffHcm = userRepository.findByEmailIgnoreCase("staff.hcm@uteexpress.vn").orElseThrow();

        long staffHcmNotifBefore = notificationRepository.countByUserIdAndIsReadFalse(staffHcm.getId());

        // 1. Customer creates inter-province shipment (TP.HCM -> Hà Nội, CASH)
        MvcResult createResult = mockMvc.perform(post("/customer/shipments")
                        .cookie(new Cookie("ACCESS_TOKEN", customerToken))
                        .param("senderName", "Shop Hoàng Nam E2E")
                        .param("senderPhone", "0901234567")
                        .param("senderProvince", "TP. Hồ Chí Minh")
                        .param("senderDistrict", "TP. Thủ Đức")
                        .param("senderAddress", "01 Võ Văn Ngân")
                        .param("receiverName", "Nguyễn Văn Kiểm Thử")
                        .param("receiverPhone", "0988777666")
                        .param("receiverProvince", "Hà Nội")
                        .param("receiverDistrict", "Quận Cầu Giấy")
                        .param("receiverAddress", "99 Xuân Thủy")
                        .param("itemName", "Laptop Test E2E")
                        .param("actualWeightKg", "1.5")
                        .param("lengthCm", "30")
                        .param("widthCm", "20")
                        .param("heightCm", "10")
                        .param("fragile", "true")
                        .param("inspectionPolicy", "ALLOW_VIEW_NO_TRY")
                        .param("serviceTypeId", String.valueOf(express.getId()))
                        .param("codAmount", "500000")
                        .param("payerType", "SENDER")
                        .param("paymentMethod", "CASH"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String redirectLocation = createResult.getResponse().getRedirectedUrl();
        assertThat(redirectLocation).isNotNull().startsWith("/customer/shipments/");
        Long shipmentId = Long.parseLong(redirectLocation.substring("/customer/shipments/".length()));

        try {
            Shipment created = shipmentRepository.findById(shipmentId).orElseThrow();
            assertThat(created.getStatus()).isEqualTo(ShipmentStatus.CREATED);
            assertThat(created.getOriginHub().getCode()).isEqualTo("HCM-TD01");
            assertThat(created.getDestHub().getCode()).isEqualTo("HN-CG01");
            assertThat(created.getTrackingCode()).startsWith("UTE");

            // Notification fired AFTER_COMMIT to origin hub staff
            long staffHcmNotifAfter = notificationRepository.countByUserIdAndIsReadFalse(staffHcm.getId());
            assertThat(staffHcmNotifAfter).isGreaterThan(staffHcmNotifBefore);

            // 2. Anti-IDOR (TC-LIFE-03): Staff HN CANNOT assign pickup at HCM-TD01
            mockMvc.perform(post("/staff/shipments/" + shipmentId + "/assign-pickup")
                            .cookie(new Cookie("ACCESS_TOKEN", staffHnToken))
                            .param("shipperId", String.valueOf(shipperHn.getId())))
                    .andExpect(status().isForbidden());

            // 3. Staff HCM assigns Shipper HCM -> ASSIGNED_PICKUP
            mockMvc.perform(post("/staff/shipments/" + shipmentId + "/assign-pickup")
                            .cookie(new Cookie("ACCESS_TOKEN", staffHcmToken))
                            .param("shipperId", String.valueOf(shipperHcm.getId())))
                    .andExpect(status().is3xxRedirection());
            assertThat(shipmentRepository.findById(shipmentId).orElseThrow().getStatus())
                    .isEqualTo(ShipmentStatus.ASSIGNED_PICKUP);

            // 4. Shipper HCM picks up -> PICKED_UP
            mockMvc.perform(post("/shipper/shipments/" + shipmentId + "/pickup")
                            .cookie(new Cookie("ACCESS_TOKEN", shipperHcmToken))
                            .param("note", "Đã kiểm hàng nguyên vẹn"))
                    .andExpect(status().is3xxRedirection());
            assertThat(shipmentRepository.findById(shipmentId).orElseThrow().getStatus())
                    .isEqualTo(ShipmentStatus.PICKED_UP);

            // 5. TC-LIFE-05: Customer CANNOT cancel after PICKED_UP
            mockMvc.perform(post("/customer/shipments/" + shipmentId + "/cancel")
                            .cookie(new Cookie("ACCESS_TOKEN", customerToken))
                            .param("reason", "Muốn hủy muộn"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attributeExists("errorMessage"));
            assertThat(shipmentRepository.findById(shipmentId).orElseThrow().getStatus())
                    .isEqualTo(ShipmentStatus.PICKED_UP);

            // 6. Staff HCM arrives origin -> dispatches inter-province transit
            mockMvc.perform(post("/staff/shipments/" + shipmentId + "/arrive-origin")
                            .cookie(new Cookie("ACCESS_TOKEN", staffHcmToken)))
                    .andExpect(status().is3xxRedirection());
            assertThat(shipmentRepository.findById(shipmentId).orElseThrow().getStatus())
                    .isEqualTo(ShipmentStatus.IN_ORIGIN_HUB);

            mockMvc.perform(post("/staff/shipments/" + shipmentId + "/dispatch-transit")
                            .cookie(new Cookie("ACCESS_TOKEN", staffHcmToken)))
                    .andExpect(status().is3xxRedirection());
            assertThat(shipmentRepository.findById(shipmentId).orElseThrow().getStatus())
                    .isEqualTo(ShipmentStatus.IN_TRANSIT);

            // 7. Staff HN arrives dest -> assigns Shipper HN
            mockMvc.perform(post("/staff/shipments/" + shipmentId + "/arrive-dest")
                            .cookie(new Cookie("ACCESS_TOKEN", staffHnToken)))
                    .andExpect(status().is3xxRedirection());
            Shipment inDest = shipmentRepository.findById(shipmentId).orElseThrow();
            assertThat(inDest.getStatus()).isEqualTo(ShipmentStatus.IN_DEST_HUB);
            assertThat(inDest.getCurrentHub().getCode()).isEqualTo("HN-CG01");

            mockMvc.perform(post("/staff/shipments/" + shipmentId + "/assign-delivery")
                            .cookie(new Cookie("ACCESS_TOKEN", staffHnToken))
                            .param("shipperId", String.valueOf(shipperHn.getId())))
                    .andExpect(status().is3xxRedirection());
            assertThat(shipmentRepository.findById(shipmentId).orElseThrow().getStatus())
                    .isEqualTo(ShipmentStatus.OUT_FOR_DELIVERY);

            // 8. Shipper HN fails 1st attempt -> Staff HN redelivers -> Deliver with POD key
            mockMvc.perform(post("/shipper/shipments/" + shipmentId + "/fail")
                            .cookie(new Cookie("ACCESS_TOKEN", shipperHnToken))
                            .param("reason", "Khách không nghe máy"))
                    .andExpect(status().is3xxRedirection());
            Shipment failedOnce = shipmentRepository.findById(shipmentId).orElseThrow();
            assertThat(failedOnce.getStatus()).isEqualTo(ShipmentStatus.DELIVERY_FAILED);
            assertThat(failedOnce.getFailedDeliveryCount()).isEqualTo(1);

            mockMvc.perform(post("/staff/shipments/" + shipmentId + "/assign-delivery")
                            .cookie(new Cookie("ACCESS_TOKEN", staffHnToken))
                            .param("shipperId", String.valueOf(shipperHn.getId())))
                    .andExpect(status().is3xxRedirection());

            shipmentService.markDelivered(shipmentId, shipperHn, "pod/test-e2e-pod.jpg", "Giao tận tay");
            Shipment delivered = shipmentRepository.findById(shipmentId).orElseThrow();
            assertThat(delivered.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
            assertThat(delivered.getCodStatus()).isEqualTo(CodStatus.HELD_BY_SHIPPER);

            // 9. Public Tracking renders masked receiver phone and DELIVERED status
            mockMvc.perform(get("/tracking").param("codes", delivered.getTrackingCode()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(delivered.getTrackingCode())))
                    .andExpect(content().string(containsString("098****666")))
                    .andExpect(content().string(not(containsString("0988777666"))));

            // 10. All portals render HTTP 200 cleanly
            mockMvc.perform(get("/customer").cookie(new Cookie("ACCESS_TOKEN", customerToken)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/staff").cookie(new Cookie("ACCESS_TOKEN", staffHcmToken)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/staff/shipments/" + shipmentId).cookie(new Cookie("ACCESS_TOKEN", staffHnToken)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/shipper/tasks").cookie(new Cookie("ACCESS_TOKEN", shipperHnToken)))
                    .andExpect(status().isOk());
        } finally {
            // Keep PostgreSQL seed data clean
            shipmentRepository.deleteById(shipmentId);
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
