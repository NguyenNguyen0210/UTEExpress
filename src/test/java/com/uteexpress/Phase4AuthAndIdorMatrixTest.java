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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class Phase4AuthAndIdorMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void tcAuthFullRoleAccessControlMatrix() throws Exception {
        String adminToken = loginAndExtractToken("admin@uteexpress.vn", "123456");
        String staffToken = loginAndExtractToken("staff.hcm@uteexpress.vn", "123456");
        String shipperToken = loginAndExtractToken("shipper.hcm@uteexpress.vn", "123456");
        String customerToken = loginAndExtractToken("shop@uteexpress.vn", "123456");

        // Guest redirected to /auth/login for all 4 portals
        mockMvc.perform(get("/customer")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/auth/login"));
        mockMvc.perform(get("/staff")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/auth/login"));
        mockMvc.perform(get("/shipper/tasks")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/auth/login"));
        mockMvc.perform(get("/admin")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/auth/login"));

        // Customer cannot access Staff, Shipper, Admin
        mockMvc.perform(get("/staff").cookie(new Cookie("ACCESS_TOKEN", customerToken))).andExpect(status().isForbidden());
        mockMvc.perform(get("/shipper/tasks").cookie(new Cookie("ACCESS_TOKEN", customerToken))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin").cookie(new Cookie("ACCESS_TOKEN", customerToken))).andExpect(status().isForbidden());

        // Staff can access /staff, forbidden on /customer, /shipper, /admin
        mockMvc.perform(get("/staff").cookie(new Cookie("ACCESS_TOKEN", staffToken))).andExpect(status().isOk());
        mockMvc.perform(get("/customer").cookie(new Cookie("ACCESS_TOKEN", staffToken))).andExpect(status().isForbidden());
        mockMvc.perform(get("/shipper/tasks").cookie(new Cookie("ACCESS_TOKEN", staffToken))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin").cookie(new Cookie("ACCESS_TOKEN", staffToken))).andExpect(status().isForbidden());

        // Shipper can access /shipper/tasks, forbidden on /customer, /staff, /admin
        mockMvc.perform(get("/shipper/tasks").cookie(new Cookie("ACCESS_TOKEN", shipperToken))).andExpect(status().isOk());
        mockMvc.perform(get("/customer").cookie(new Cookie("ACCESS_TOKEN", shipperToken))).andExpect(status().isForbidden());
        mockMvc.perform(get("/staff").cookie(new Cookie("ACCESS_TOKEN", shipperToken))).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin").cookie(new Cookie("ACCESS_TOKEN", shipperToken))).andExpect(status().isForbidden());

        // Admin can access /admin and /staff, forbidden on /customer and /shipper
        mockMvc.perform(get("/admin").cookie(new Cookie("ACCESS_TOKEN", adminToken))).andExpect(status().isOk());
        mockMvc.perform(get("/staff").cookie(new Cookie("ACCESS_TOKEN", adminToken))).andExpect(status().isOk());
        mockMvc.perform(get("/customer").cookie(new Cookie("ACCESS_TOKEN", adminToken))).andExpect(status().isForbidden());
        mockMvc.perform(get("/shipper/tasks").cookie(new Cookie("ACCESS_TOKEN", adminToken))).andExpect(status().isForbidden());

        // API 403 JSON for Staff & Shipper on /api/v1/admin/dashboard/charts
        mockMvc.perform(get("/api/v1/admin/dashboard/charts")
                        .cookie(new Cookie("ACCESS_TOKEN", staffToken))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mockMvc.perform(get("/api/v1/admin/dashboard/charts")
                        .cookie(new Cookie("ACCESS_TOKEN", shipperToken))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void tcAuth02LockedAccountCannotLoginAndExistingJwtIsRejected() throws Exception {
        String adminToken = loginAndExtractToken("admin@uteexpress.vn", "123456");
        String suffix = String.valueOf(System.currentTimeMillis()).substring(7);
        String email = "locked.test." + suffix + "@uteexpress.vn";

        User tempCustomer = userRepository.save(User.builder()
                .email(email)
                .phone("0977" + suffix)
                .passwordHash(passwordEncoder.encode("123456"))
                .fullName("Khách Hàng Test Khóa")
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build());

        try {
            // Login while ACTIVE succeeds
            String issuedToken = loginAndExtractToken(email, "123456");
            mockMvc.perform(get("/customer").cookie(new Cookie("ACCESS_TOKEN", issuedToken)))
                    .andExpect(status().isOk());

            // Admin locks account via POST /admin/users/{id}/toggle-status
            mockMvc.perform(post("/admin/users/" + tempCustomer.getId() + "/toggle-status")
                            .cookie(new Cookie("ACCESS_TOKEN", adminToken)))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/users"));

            assertThat(userRepository.findById(tempCustomer.getId()).orElseThrow().getStatus())
                    .isEqualTo(UserStatus.LOCKED);

            // Attempt to login again -> rejected without ACCESS_TOKEN cookie
            MvcResult failedLogin = mockMvc.perform(post("/auth/login")
                            .param("email", email)
                            .param("password", "123456"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("bị khóa")))
                    .andReturn();
            assertThat(failedLogin.getResponse().getHeader("Set-Cookie")).isNull();

            // Previously issued JWT cookie is immediately rejected by JwtAuthenticationFilter
            mockMvc.perform(get("/customer").cookie(new Cookie("ACCESS_TOKEN", issuedToken)))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/auth/login"));
        } finally {
            userRepository.deleteById(tempCustomer.getId());
        }
    }

    @Test
    void tcAuth03CrossCustomerIdorBlockedAcrossDetailCancelWaybillPodAndChat() throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis()).substring(7);
        String intruderEmail = "intruder." + suffix + "@uteexpress.vn";

        User customerB = userRepository.save(User.builder()
                .email(intruderEmail)
                .phone("0966" + suffix)
                .passwordHash(passwordEncoder.encode("123456"))
                .fullName("Khách Hàng B (Không phải chủ đơn)")
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build());

        try {
            String tokenB = loginAndExtractToken(intruderEmail, "123456");
            Shipment shipmentOfA = shipmentRepository.findByTrackingCodeIgnoreCase("UTE261120A8K4P2").orElseThrow();

            mockMvc.perform(get("/customer/shipments/" + shipmentOfA.getId())
                            .cookie(new Cookie("ACCESS_TOKEN", tokenB)))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/customer/shipments/" + shipmentOfA.getId() + "/cancel")
                            .cookie(new Cookie("ACCESS_TOKEN", tokenB))
                            .param("reason", "Cố hủy đơn của người khác"))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/shipments/" + shipmentOfA.getId() + "/waybill")
                            .cookie(new Cookie("ACCESS_TOKEN", tokenB)))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/files/pod/" + shipmentOfA.getId())
                            .cookie(new Cookie("ACCESS_TOKEN", tokenB)))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/v1/chat/" + shipmentOfA.getId() + "/messages")
                            .cookie(new Cookie("ACCESS_TOKEN", tokenB))
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        } finally {
            userRepository.deleteById(customerB.getId());
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
