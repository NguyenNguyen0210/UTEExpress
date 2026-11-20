package com.uteexpress.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedApiRequestShouldReturn401JsonNotHtmlRedirect() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void unauthenticatedWebPortalShouldRedirectToLogin() throws Exception {
        mockMvc.perform(get("/admin/hubs"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login"));
    }

    @Test
    void adminLoginSetsHttpOnlyCookieAndAllowsAccessToAdminHubs() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .param("email", "admin@uteexpress.vn")
                        .param("password", "123456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"))
                .andReturn();

        String setCookie = loginResult.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull().contains("ACCESS_TOKEN=").contains("HttpOnly");

        String token = extractCookieValue(setCookie, "ACCESS_TOKEN");
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/admin/hubs").cookie(new Cookie("ACCESS_TOKEN", token)))
                .andExpect(status().isOk());
    }

    @Test
    void customerShouldReceive403WhenAccessingAdminPortal() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .param("email", "shop@uteexpress.vn")
                        .param("password", "123456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer"))
                .andReturn();

        String token = extractCookieValue(loginResult.getResponse().getHeader("Set-Cookie"), "ACCESS_TOKEN");

        mockMvc.perform(get("/admin/hubs").cookie(new Cookie("ACCESS_TOKEN", token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutShouldClearAccessTokenCookie() throws Exception {
        MvcResult logoutResult = mockMvc.perform(post("/auth/logout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login"))
                .andReturn();

        String setCookie = logoutResult.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull().contains("ACCESS_TOKEN=").contains("Max-Age=0");
    }

    private String extractCookieValue(String setCookieHeader, String cookieName) {
        if (setCookieHeader == null) return "";
        String prefix = cookieName + "=";
        int start = setCookieHeader.indexOf(prefix);
        if (start < 0) return "";
        int end = setCookieHeader.indexOf(';', start);
        return end > 0 ? setCookieHeader.substring(start + prefix.length(), end)
                : setCookieHeader.substring(start + prefix.length());
    }
}
