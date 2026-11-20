package com.uteexpress;

import com.uteexpress.entity.User;
import com.uteexpress.enums.Role;
import com.uteexpress.enums.UserStatus;
import com.uteexpress.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CouldHaveCodExportAndAccountTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void codCsvExportsForAdminAndCustomerReturnUtf8BomAndEnforceRoleAccess() throws Exception {
        String adminToken = loginAndExtractToken("admin@uteexpress.vn", "123456");
        String customerToken = loginAndExtractToken("shop@uteexpress.vn", "123456");

        // Admin COD CSV export -> 200 UTF-8 BOM CSV
        byte[] adminBytes = mockMvc.perform(get("/admin/cod-settlements/export")
                        .cookie(new Cookie("ACCESS_TOKEN", adminToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(adminBytes[0]).isEqualTo((byte) 0xEF);
        assertThat(adminBytes[1]).isEqualTo((byte) 0xBB);
        assertThat(adminBytes[2]).isEqualTo((byte) 0xBF);
        assertThat(new String(adminBytes, 3, adminBytes.length - 3, StandardCharsets.UTF_8))
                .contains("Mã phiếu đối soát").contains("Số tiền thực chuyển");

        // Customer blocked on Admin COD export -> 403
        mockMvc.perform(get("/admin/cod-settlements/export")
                        .cookie(new Cookie("ACCESS_TOKEN", customerToken)))
                .andExpect(status().isForbidden());

        // Customer COD CSV export -> 200 UTF-8 BOM CSV
        byte[] custBytes = mockMvc.perform(get("/customer/cod/export")
                        .cookie(new Cookie("ACCESS_TOKEN", customerToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(custBytes[0]).isEqualTo((byte) 0xEF);
        assertThat(new String(custBytes, 3, custBytes.length - 3, StandardCharsets.UTF_8))
                .contains("Mã phiếu đối soát");

        // Public tracking renders share link helper
        mockMvc.perform(get("/tracking").param("codes", "UTE261120A8K4P2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("copyTrackingLink")));
    }

    @Test
    void changePasswordValidatesOldPasswordAndUpdatesBcryptHash() throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis()).substring(7);
        String email = "pwd.test." + suffix + "@uteexpress.vn";
        User tempUser = userRepository.save(User.builder()
                .email(email)
                .phone("0939" + suffix)
                .passwordHash(passwordEncoder.encode("123456"))
                .fullName("Người Dùng Đổi Mật Khẩu")
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build());

        try {
            String token = loginAndExtractToken(email, "123456");

            mockMvc.perform(get("/account/password").cookie(new Cookie("ACCESS_TOKEN", token)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Cập nhật mật khẩu")));

            // Wrong current password -> rejected
            mockMvc.perform(post("/account/password")
                            .cookie(new Cookie("ACCESS_TOKEN", token))
                            .param("currentPassword", "wrongpass")
                            .param("newPassword", "newpass123")
                            .param("confirmPassword", "newpass123"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attributeExists("errorMessage"));

            // Correct current password -> updated cleanly
            mockMvc.perform(post("/account/password")
                            .cookie(new Cookie("ACCESS_TOKEN", token))
                            .param("currentPassword", "123456")
                            .param("newPassword", "newpass123")
                            .param("confirmPassword", "newpass123"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attributeExists("successMessage"));

            // Verify login succeeds with new password
            String freshToken = loginAndExtractToken(email, "newpass123");
            assertThat(freshToken).isNotBlank();
        } finally {
            userRepository.deleteById(tempUser.getId());
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
