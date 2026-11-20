package com.uteexpress;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class Phase1WebSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allPublicPagesAndApisRenderWithoutThymeleafErrors() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("UTEExpress")));

        mockMvc.perform(get("/tracking").param("codes", "UTE261120A8K4P2,UTE261119B7M2Q9,INVALID123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("UTE261120A8K4P2")))
                .andExpect(content().string(containsString("090****567")))
                .andExpect(content().string(not(containsString("0901234567"))))
                .andExpect(content().string(not(containsString("0987654321"))));

        mockMvc.perform(get("/estimate"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tiêu chuẩn")));

        mockMvc.perform(get("/hubs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("HCM-TD01")));

        mockMvc.perform(get("/auth/login")).andExpect(status().isOk());
        mockMvc.perform(get("/auth/register")).andExpect(status().isOk());

        // Public Estimate API (TC-PRICE-02)
        String estimateJson = """
                {
                  "senderProvince": "TP. Hồ Chí Minh",
                  "receiverProvince": "Hà Nội",
                  "actualWeightKg": 1.0,
                  "lengthCm": 30,
                  "widthCm": 20,
                  "heightCm": 15,
                  "codAmount": 500000
                }
                """;
        mockMvc.perform(post("/api/v1/public/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(estimateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interProvince").value(true))
                .andExpect(jsonPath("$.originHubCode").value("HCM-TD01"))
                .andExpect(jsonPath("$.destHubCode").value("HN-CG01"))
                .andExpect(jsonPath("$.volumetricWeightKg").value(1.8))
                .andExpect(jsonPath("$.chargeableWeightKg").value(2.0))
                .andExpect(jsonPath("$.options[0].code").value("STANDARD"))
                .andExpect(jsonPath("$.options[0].shippingFee").value(40000))
                .andExpect(jsonPath("$.options[0].codFee").value(5000))
                .andExpect(jsonPath("$.options[0].totalFee").value(45000));
    }

    @Test
    void customerAndAdminPagesRenderWithValidJwtCookie() throws Exception {
        // Customer pages
        String customerToken = loginAndExtractToken("shop@uteexpress.vn", "123456");
        mockMvc.perform(get("/customer/addresses").cookie(new Cookie("ACCESS_TOKEN", customerToken)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Kho Chính Thủ Đức")));

        mockMvc.perform(get("/customer/profile").cookie(new Cookie("ACCESS_TOKEN", customerToken)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vietcombank")));

        mockMvc.perform(get("/api/v1/notifications").cookie(new Cookie("ACCESS_TOKEN", customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications").isArray());

        // Admin pages
        String adminToken = loginAndExtractToken("admin@uteexpress.vn", "123456");
        mockMvc.perform(get("/admin/hubs").cookie(new Cookie("ACCESS_TOKEN", adminToken)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("HCM-TD01")));
        mockMvc.perform(get("/admin/services").cookie(new Cookie("ACCESS_TOKEN", adminToken)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("STANDARD")));
        mockMvc.perform(get("/admin/users").cookie(new Cookie("ACCESS_TOKEN", adminToken)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("staff.hcm@uteexpress.vn")));
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
