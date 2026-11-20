package com.uteexpress;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CouldHaveDarkModeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void portalLayoutRendersDarkModeToggleActiveSidebarAndChartThemeSync() throws Exception {
        String adminToken = loginAndExtractToken("admin@uteexpress.vn", "123456");
        String customerToken = loginAndExtractToken("shop@uteexpress.vn", "123456");

        mockMvc.perform(get("/admin").cookie(new Cookie("ACCESS_TOKEN", adminToken)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("portalThemeToggleBtn")))
                .andExpect(content().string(containsString("uteexpress-theme")))
                .andExpect(content().string(containsString("highlightActiveSidebarLinks")))
                .andExpect(content().string(containsString("ute-theme-changed")));

        mockMvc.perform(get("/customer").cookie(new Cookie("ACCESS_TOKEN", customerToken)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("portalThemeToggleBtn")));
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
