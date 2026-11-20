package com.uteexpress;

import com.uteexpress.config.VnPayConfig;
import com.uteexpress.entity.ServiceType;
import com.uteexpress.entity.Shipment;
import com.uteexpress.enums.PaymentStatus;
import com.uteexpress.enums.ShipmentStatus;
import com.uteexpress.repository.ServiceTypeRepository;
import com.uteexpress.repository.ShipmentRepository;
import com.uteexpress.service.VnPayService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class Phase4PricingVnPayAndCancelTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServiceTypeRepository serviceTypeRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private VnPayService vnPayService;

    @Autowired
    private VnPayConfig vnPayConfig;

    @Test
    void tcPrice03ServiceFeeChangePreservesOldShipmentFeesAndAppliesToNewEstimates() throws Exception {
        ServiceType standard = serviceTypeRepository.findByCodeIgnoreCase("STANDARD").orElseThrow();
        BigDecimal originalBaseFee = standard.getBaseFee();
        Shipment existingShipment = shipmentRepository.findByTrackingCodeIgnoreCase("UTE261119B7M2Q9").orElseThrow();
        BigDecimal originalExistingTotalFee = existingShipment.getTotalFee();

        try {
            // Update STANDARD baseFee from 20,000 -> 25,000
            standard.setBaseFee(new BigDecimal("25000.00"));
            serviceTypeRepository.save(standard);

            // Existing shipment fee snapshot MUST remain completely unchanged
            Shipment reloaded = shipmentRepository.findById(existingShipment.getId()).orElseThrow();
            assertThat(reloaded.getTotalFee()).isEqualByComparingTo(originalExistingTotalFee);

            // New estimate call uses the updated 25,000 baseFee (intra-province 0.8kg -> 1.0kg -> 25,000)
            String body = """
                    {
                      "senderProvince": "TP. Hồ Chí Minh",
                      "receiverProvince": "TP. Hồ Chí Minh",
                      "serviceTypeId": %d,
                      "actualWeightKg": 0.8,
                      "codAmount": 0
                    }
                    """.formatted(standard.getId());

            mockMvc.perform(post("/api/v1/public/estimate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.options[0].shippingFee").value(25000))
                    .andExpect(jsonPath("$.options[0].totalFee").value(25000));
        } finally {
            standard.setBaseFee(originalBaseFee);
            serviceTypeRepository.save(standard);
        }
    }

    @Test
    void tcVnPay02CancelledPaymentKeepsPendingPaymentAndTcLife04CancelPaidSetsRefundPending() throws Exception {
        String customerToken = loginAndExtractToken("shop@uteexpress.vn", "123456");
        ServiceType express = serviceTypeRepository.findByCodeIgnoreCase("EXPRESS").orElseThrow();

        // 1. Create SENDER + VNPAY shipment
        MvcResult createRes = mockMvc.perform(post("/customer/shipments")
                        .cookie(new Cookie("ACCESS_TOKEN", customerToken))
                        .param("senderName", "Shop Hoàng Nam Tech")
                        .param("senderPhone", "0901234567")
                        .param("senderProvince", "TP. Hồ Chí Minh")
                        .param("senderDistrict", "TP. Thủ Đức")
                        .param("senderAddress", "01 Võ Văn Ngân")
                        .param("receiverName", "Khách Hủy VNPay")
                        .param("receiverPhone", "0988123123")
                        .param("receiverProvince", "Hà Nội")
                        .param("receiverDistrict", "Quận Cầu Giấy")
                        .param("receiverAddress", "50 Xuân Thủy")
                        .param("itemName", "Chuột Test Hủy VNPay")
                        .param("actualWeightKg", "1.0")
                        .param("fragile", "true")
                        .param("inspectionPolicy", "ALLOW_VIEW_NO_TRY")
                        .param("serviceTypeId", String.valueOf(express.getId()))
                        .param("codAmount", "0")
                        .param("payerType", "SENDER")
                        .param("paymentMethod", "VNPAY"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String txnRef = extractQueryParam(createRes.getResponse().getRedirectedUrl(), "vnp_TxnRef");
        Shipment created = shipmentRepository.findByVnpTxnRef(txnRef).orElseThrow();

        try {
            // 2. TC-VNPAY-02: Simulate user cancelling at VNPay Sandbox (vnp_ResponseCode = 24)
            Map<String, String> cancelParams = new LinkedHashMap<>();
            cancelParams.put("vnp_TmnCode", vnPayConfig.getTmnCode());
            cancelParams.put("vnp_Amount", created.getTotalFee()
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .toPlainString());
            cancelParams.put("vnp_BankCode", "NCB");
            cancelParams.put("vnp_OrderInfo", "Thanh toan cuoc van don " + created.getTrackingCode());
            cancelParams.put("vnp_ResponseCode", "24");
            cancelParams.put("vnp_TransactionStatus", "02");
            cancelParams.put("vnp_TxnRef", txnRef);

            String signedQuery = vnPayService.buildSignedQuery(cancelParams, vnPayConfig.getHashSecret());
            String secureHash = extractQueryParam("?" + signedQuery, "vnp_SecureHash");

            MockHttpServletRequestBuilder cancelReturnReq = get("/payment/vnpay-return");
            cancelParams.forEach(cancelReturnReq::param);
            cancelReturnReq.param("vnp_SecureHash", secureHash);

            mockMvc.perform(cancelReturnReq)
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("retry-vnpay")));

            Shipment stillPending = shipmentRepository.findById(created.getId()).orElseThrow();
            assertThat(stillPending.getStatus()).isEqualTo(ShipmentStatus.PENDING_PAYMENT);
            assertThat(stillPending.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);

            // 3. Retry VNPay -> generates fresh valid Sandbox URL
            MvcResult retryRes = mockMvc.perform(post("/customer/shipments/" + created.getId() + "/retry-vnpay")
                            .cookie(new Cookie("ACCESS_TOKEN", customerToken)))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();
            assertThat(retryRes.getResponse().getRedirectedUrl()).startsWith(vnPayConfig.getPayUrl());

            // 4. Complete VNPay payment successfully -> CREATED + PAID
            String freshTxnRef = extractQueryParam(retryRes.getResponse().getRedirectedUrl(), "vnp_TxnRef");
            Map<String, String> paidParams = new LinkedHashMap<>();
            paidParams.put("vnp_TmnCode", vnPayConfig.getTmnCode());
            paidParams.put("vnp_Amount", created.getTotalFee()
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .toPlainString());
            paidParams.put("vnp_BankCode", "NCB");
            paidParams.put("vnp_OrderInfo", "Thanh toan cuoc van don " + created.getTrackingCode());
            paidParams.put("vnp_ResponseCode", "00");
            paidParams.put("vnp_TransactionStatus", "00");
            paidParams.put("vnp_TransactionNo", "14888888");
            paidParams.put("vnp_TxnRef", freshTxnRef);

            String paidQuery = vnPayService.buildSignedQuery(paidParams, vnPayConfig.getHashSecret());
            String paidHash = extractQueryParam("?" + paidQuery, "vnp_SecureHash");

            MockHttpServletRequestBuilder paidReq = get("/payment/vnpay-return");
            paidParams.forEach(paidReq::param);
            paidReq.param("vnp_SecureHash", paidHash);
            mockMvc.perform(paidReq).andExpect(status().isOk());

            Shipment paid = shipmentRepository.findById(created.getId()).orElseThrow();
            assertThat(paid.getStatus()).isEqualTo(ShipmentStatus.CREATED);
            assertThat(paid.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

            // 5. TC-MEDIA-04: Waybill PDF A6 & A5 for fragile Vietnamese shipment
            MvcResult pdfA6 = mockMvc.perform(get("/shipments/" + paid.getId() + "/waybill")
                            .cookie(new Cookie("ACCESS_TOKEN", customerToken)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                    .andReturn();
            assertThat(pdfA6.getResponse().getContentAsByteArray().length).isGreaterThan(10_000);

            // 6. TC-LIFE-04: Customer cancels PAID CREATED shipment -> CANCELLED + REFUND_PENDING
            mockMvc.perform(post("/customer/shipments/" + paid.getId() + "/cancel")
                            .cookie(new Cookie("ACCESS_TOKEN", customerToken))
                            .param("reason", "Đổi kế hoạch gửi hàng"))
                    .andExpect(status().is3xxRedirection());

            Shipment cancelled = shipmentRepository.findById(paid.getId()).orElseThrow();
            assertThat(cancelled.getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
            assertThat(cancelled.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        } finally {
            shipmentRepository.deleteById(created.getId());
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
