package com.uteexpress;

import com.uteexpress.entity.ServiceType;
import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.User;
import com.uteexpress.enums.ShipmentStatus;
import com.uteexpress.repository.ServiceTypeRepository;
import com.uteexpress.repository.ShipmentRepository;
import com.uteexpress.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class Phase4LifecycleAndPodValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private ServiceTypeRepository serviceTypeRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void tcLife02IntraHubTcLife03CrossHubIdorTcMedia0102AndTcLife06ThreeFailuresReturnFlow() throws Exception {
        String customerToken = loginAndExtractToken("shop@uteexpress.vn", "123456");
        String staffHcmToken = loginAndExtractToken("staff.hcm@uteexpress.vn", "123456");
        String staffHnToken = loginAndExtractToken("staff.hn@uteexpress.vn", "123456");
        String shipperHcmToken = loginAndExtractToken("shipper.hcm@uteexpress.vn", "123456");

        ServiceType standard = serviceTypeRepository.findByCodeIgnoreCase("STANDARD").orElseThrow();
        User shipperHcm = userRepository.findByEmailIgnoreCase("shipper.hcm@uteexpress.vn").orElseThrow();

        // 1. Create Intra-Hub shipment (HCM-TD01 -> HCM-TD01)
        MvcResult createRes = mockMvc.perform(post("/customer/shipments")
                        .cookie(new Cookie("ACCESS_TOKEN", customerToken))
                        .param("senderName", "Shop Hoàng Nam Nội Vùng")
                        .param("senderPhone", "0901234567")
                        .param("senderProvince", "TP. Hồ Chí Minh")
                        .param("senderDistrict", "TP. Thủ Đức")
                        .param("senderAddress", "01 Võ Văn Ngân")
                        .param("receiverName", "Khách Nội Vùng Thủ Đức")
                        .param("receiverPhone", "0933444555")
                        .param("receiverProvince", "TP. Hồ Chí Minh")
                        .param("receiverDistrict", "TP. Thủ Đức")
                        .param("receiverAddress", "200 Kha Vạn Cân")
                        .param("itemName", "Cáp sạc USB-C Test")
                        .param("actualWeightKg", "0.5")
                        .param("inspectionPolicy", "ALLOW_VIEW_NO_TRY")
                        .param("serviceTypeId", String.valueOf(standard.getId()))
                        .param("codAmount", "150000")
                        .param("payerType", "SENDER")
                        .param("paymentMethod", "CASH"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        Long shipmentId = Long.parseLong(
                createRes.getResponse().getRedirectedUrl().substring("/customer/shipments/".length()));

        try {
            Shipment created = shipmentRepository.findById(shipmentId).orElseThrow();
            assertThat(created.getOriginHub().getCode()).isEqualTo("HCM-TD01");
            assertThat(created.getDestHub().getCode()).isEqualTo("HCM-TD01");

            // 2. TC-LIFE-03: Staff HN (unrelated hub) receives 403 viewing or operating on intra-HCM shipment
            mockMvc.perform(get("/staff/shipments/" + shipmentId)
                            .cookie(new Cookie("ACCESS_TOKEN", staffHnToken)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/staff/shipments/" + shipmentId + "/assign-pickup")
                            .cookie(new Cookie("ACCESS_TOKEN", staffHnToken))
                            .param("shipperId", String.valueOf(shipperHcm.getId())))
                    .andExpect(status().isForbidden());

            // 3. TC-LIFE-02: Intra-hub progression skips IN_TRANSIT (IN_ORIGIN_HUB -> IN_DEST_HUB directly)
            mockMvc.perform(post("/staff/shipments/" + shipmentId + "/assign-pickup")
                            .cookie(new Cookie("ACCESS_TOKEN", staffHcmToken))
                            .param("shipperId", String.valueOf(shipperHcm.getId())))
                    .andExpect(status().is3xxRedirection());
            mockMvc.perform(post("/shipper/shipments/" + shipmentId + "/pickup")
                            .cookie(new Cookie("ACCESS_TOKEN", shipperHcmToken)))
                    .andExpect(status().is3xxRedirection());
            mockMvc.perform(post("/staff/shipments/" + shipmentId + "/arrive-origin")
                            .cookie(new Cookie("ACCESS_TOKEN", staffHcmToken)))
                    .andExpect(status().is3xxRedirection());
            assertThat(shipmentRepository.findById(shipmentId).orElseThrow().getStatus())
                    .isEqualTo(ShipmentStatus.IN_ORIGIN_HUB);

            // Dispatch transit MUST fail on intra-hub shipment
            mockMvc.perform(post("/staff/shipments/" + shipmentId + "/dispatch-transit")
                            .cookie(new Cookie("ACCESS_TOKEN", staffHcmToken)))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attributeExists("errorMessage"));
            assertThat(shipmentRepository.findById(shipmentId).orElseThrow().getStatus())
                    .isEqualTo(ShipmentStatus.IN_ORIGIN_HUB);

            // Move directly IN_ORIGIN_HUB -> IN_DEST_HUB
            mockMvc.perform(post("/staff/shipments/" + shipmentId + "/arrive-dest")
                            .cookie(new Cookie("ACCESS_TOKEN", staffHcmToken)))
                    .andExpect(status().is3xxRedirection());
            assertThat(shipmentRepository.findById(shipmentId).orElseThrow().getStatus())
                    .isEqualTo(ShipmentStatus.IN_DEST_HUB);

            // 4. Assign delivery -> OUT_FOR_DELIVERY
            mockMvc.perform(post("/staff/shipments/" + shipmentId + "/assign-delivery")
                            .cookie(new Cookie("ACCESS_TOKEN", staffHcmToken))
                            .param("shipperId", String.valueOf(shipperHcm.getId())))
                    .andExpect(status().is3xxRedirection());
            assertThat(shipmentRepository.findById(shipmentId).orElseThrow().getStatus())
                    .isEqualTo(ShipmentStatus.OUT_FOR_DELIVERY);

            // 5. TC-MEDIA-01: Deliver without POD image -> rejected, stays OUT_FOR_DELIVERY
            mockMvc.perform(multipart("/shipper/shipments/" + shipmentId + "/deliver")
                            .cookie(new Cookie("ACCESS_TOKEN", shipperHcmToken))
                            .param("note", "Thiếu ảnh"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attributeExists("errorMessage"));
            assertThat(shipmentRepository.findById(shipmentId).orElseThrow().getStatus())
                    .isEqualTo(ShipmentStatus.OUT_FOR_DELIVERY);

            // 6. TC-MEDIA-02: Deliver with invalid .pdf file -> rejected, stays OUT_FOR_DELIVERY
            MockMultipartFile fakePdf = new MockMultipartFile(
                    "podImage", "document.pdf", "application/pdf", "%PDF-1.4 fake".getBytes());
            mockMvc.perform(multipart("/shipper/shipments/" + shipmentId + "/deliver")
                            .file(fakePdf)
                            .cookie(new Cookie("ACCESS_TOKEN", shipperHcmToken)))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attributeExists("errorMessage"));
            assertThat(shipmentRepository.findById(shipmentId).orElseThrow().getStatus())
                    .isEqualTo(ShipmentStatus.OUT_FOR_DELIVERY);

            // 7. TC-LIFE-06 (BR-08): Fail delivery 3 times -> 4th redelivery blocked -> RETURNING -> RETURNED
            // Attempt 1 fail + redeliver
            mockMvc.perform(post("/shipper/shipments/" + shipmentId + "/fail")
                            .cookie(new Cookie("ACCESS_TOKEN", shipperHcmToken))
                            .param("reason", "Lần 1: Khách không nghe máy"))
                    .andExpect(status().is3xxRedirection());
            mockMvc.perform(post("/staff/shipments/" + shipmentId + "/assign-delivery")
                            .cookie(new Cookie("ACCESS_TOKEN", staffHcmToken))
                            .param("shipperId", String.valueOf(shipperHcm.getId())))
                    .andExpect(status().is3xxRedirection());

            // Attempt 2 fail + redeliver
            mockMvc.perform(post("/shipper/shipments/" + shipmentId + "/fail")
                            .cookie(new Cookie("ACCESS_TOKEN", shipperHcmToken))
                            .param("reason", "Lần 2: Thuê bao không liên lạc được"))
                    .andExpect(status().is3xxRedirection());
            mockMvc.perform(post("/staff/shipments/" + shipmentId + "/assign-delivery")
                            .cookie(new Cookie("ACCESS_TOKEN", staffHcmToken))
                            .param("shipperId", String.valueOf(shipperHcm.getId())))
                    .andExpect(status().is3xxRedirection());

            // Attempt 3 fail -> failedDeliveryCount == 3
            mockMvc.perform(post("/shipper/shipments/" + shipmentId + "/fail")
                            .cookie(new Cookie("ACCESS_TOKEN", shipperHcmToken))
                            .param("reason", "Lần 3: Khách từ chối nhận hàng"))
                    .andExpect(status().is3xxRedirection());
            Shipment after3Fails = shipmentRepository.findById(shipmentId).orElseThrow();
            assertThat(after3Fails.getStatus()).isEqualTo(ShipmentStatus.DELIVERY_FAILED);
            assertThat(after3Fails.getFailedDeliveryCount()).isEqualTo(3);

            // Redelivering a 4th time MUST be rejected by BR-08
            mockMvc.perform(post("/staff/shipments/" + shipmentId + "/assign-delivery")
                            .cookie(new Cookie("ACCESS_TOKEN", staffHcmToken))
                            .param("shipperId", String.valueOf(shipperHcm.getId())))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attributeExists("errorMessage"));
            assertThat(shipmentRepository.findById(shipmentId).orElseThrow().getStatus())
                    .isEqualTo(ShipmentStatus.DELIVERY_FAILED);

            // Start return -> RETURNING -> Complete return -> RETURNED
            mockMvc.perform(post("/staff/shipments/" + shipmentId + "/start-return")
                            .cookie(new Cookie("ACCESS_TOKEN", staffHcmToken))
                            .param("reason", "Giao thất bại đủ 3 lần"))
                    .andExpect(status().is3xxRedirection());
            assertThat(shipmentRepository.findById(shipmentId).orElseThrow().getStatus())
                    .isEqualTo(ShipmentStatus.RETURNING);

            mockMvc.perform(post("/staff/shipments/" + shipmentId + "/complete-return")
                            .cookie(new Cookie("ACCESS_TOKEN", staffHcmToken))
                            .param("note", "Chủ shop đã ký nhận lại hàng"))
                    .andExpect(status().is3xxRedirection());
            assertThat(shipmentRepository.findById(shipmentId).orElseThrow().getStatus())
                    .isEqualTo(ShipmentStatus.RETURNED);
        } finally {
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
