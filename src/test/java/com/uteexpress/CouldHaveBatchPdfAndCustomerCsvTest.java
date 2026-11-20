package com.uteexpress;

import com.uteexpress.entity.Shipment;
import com.uteexpress.entity.User;
import com.uteexpress.enums.Role;
import com.uteexpress.enums.UserStatus;
import com.uteexpress.repository.ShipmentRepository;
import com.uteexpress.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CouldHaveBatchPdfAndCustomerCsvTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void batchWaybillPdfGeneratesMultiPagePdfAndEnforcesAntiIdor() throws Exception {
        String customerToken = loginAndExtractToken("shop@uteexpress.vn", "123456");
        String shipperToken = loginAndExtractToken("shipper.hcm@uteexpress.vn", "123456");

        Shipment s1 = shipmentRepository.findByTrackingCodeIgnoreCase("UTE261120A8K4P2").orElseThrow();
        Shipment s2 = shipmentRepository.findByTrackingCodeIgnoreCase("UTE261119B7M2Q9").orElseThrow();

        // Single PDF baseline
        byte[] singlePdf = mockMvc.perform(get("/shipments/" + s1.getId() + "/waybill")
                        .cookie(new Cookie("ACCESS_TOKEN", customerToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        // Batch PDF (2 shipments) -> valid multi-page %PDF- larger than single PDF
        byte[] batchPdf = mockMvc.perform(post("/shipments/waybill/batch")
                        .cookie(new Cookie("ACCESS_TOKEN", customerToken))
                        .param("shipmentIds", String.valueOf(s1.getId()), String.valueOf(s2.getId()))
                        .param("size", "A6"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(batchPdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(batchPdf.length).isGreaterThan(singlePdf.length);

        // Shipper must be denied (403)
        mockMvc.perform(post("/shipments/waybill/batch")
                        .cookie(new Cookie("ACCESS_TOKEN", shipperToken))
                        .param("shipmentIds", String.valueOf(s1.getId())))
                .andExpect(status().isForbidden());

        // Cross-customer IDOR: another customer trying to batch-print shop's shipments gets 403
        String suffix = String.valueOf(System.currentTimeMillis()).substring(7);
        User otherCustomer = userRepository.save(User.builder()
                .email("batch.intruder." + suffix + "@uteexpress.vn")
                .phone("0919" + suffix)
                .passwordHash(passwordEncoder.encode("123456"))
                .fullName("Khách Khác")
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build());
        try {
            String otherToken = loginAndExtractToken(otherCustomer.getEmail(), "123456");
            mockMvc.perform(post("/shipments/waybill/batch")
                            .cookie(new Cookie("ACCESS_TOKEN", otherToken))
                            .param("shipmentIds", String.valueOf(s1.getId()), String.valueOf(s2.getId())))
                    .andExpect(status().isForbidden());
        } finally {
            userRepository.deleteById(otherCustomer.getId());
        }
    }

    @Test
    void customerCsvExportReturnsUtf8BomCsvWithSeedShipments() throws Exception {
        String customerToken = loginAndExtractToken("shop@uteexpress.vn", "123456");

        MvcResult res = mockMvc.perform(get("/customer/shipments/export")
                        .cookie(new Cookie("ACCESS_TOKEN", customerToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".csv")))
                .andReturn();

        byte[] bytes = res.getResponse().getContentAsByteArray();
        assertThat(bytes.length).isGreaterThan(3);
        // UTF-8 BOM: EF BB BF
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);

        String csvText = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        assertThat(csvText).contains("Mã vận đơn").contains("UTE261120A8K4P2").contains("UTE261119B7M2Q9");
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
