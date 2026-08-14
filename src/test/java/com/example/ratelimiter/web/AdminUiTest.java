package com.example.ratelimiter.web;

import com.example.ratelimiter.controller.dto.QuotaResponse;
import com.example.ratelimiter.controller.dto.RuleResponse;
import com.example.ratelimiter.domain.RateLimitAlgorithm;
import com.example.ratelimiter.service.QuotaService;
import com.example.ratelimiter.service.RateLimitRuleService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUiTest {

    private static final String API_KEY = "test-api-key";
    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RateLimitRuleService ruleService;

    @MockitoBean
    private QuotaService quotaService;

    @Test
    void loginPageRendersWithoutSession() throws Exception {
        mockMvc.perform(get("/drl/admin/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/login"))
                .andExpect(content().string(containsString("API key")));
    }

    @Test
    void loginWithValidKeyRedirectsToAdmin() throws Exception {
        mockMvc.perform(post("/drl/admin/login").param("apiKey", API_KEY))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/drl/admin"));
    }

    @Test
    void loginWithInvalidKeyRedirectsWithError() throws Exception {
        mockMvc.perform(post("/drl/admin/login").param("apiKey", "wrong"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/drl/admin/login?error"));
    }

    @Test
    void rulesPageRequiresSession() throws Exception {
        mockMvc.perform(get("/drl/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/drl/admin/login"));
    }

    @Test
    void rulesPageRendersWhenAuthenticated() throws Exception {
        when(ruleService.list(anyInt())).thenReturn(List.of(sampleRule()));
        MockHttpSession session = authenticatedSession();

        mockMvc.perform(get("/drl/admin").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/rules"))
                .andExpect(content().string(containsString("tenant-42")))
                .andExpect(content().string(containsString("checkout")));
    }

    @Test
    void quotasPageRendersWhenAuthenticated() throws Exception {
        when(ruleService.list(anyInt())).thenReturn(List.of(sampleRule()));
        when(quotaService.observe(eq("tenant-42"), eq("checkout")))
                .thenReturn(QuotaResponse.of(
                        "tenant-42",
                        "checkout",
                        RateLimitAlgorithm.TOKEN_BUCKET,
                        10,
                        90,
                        100,
                        null,
                        null,
                        null,
                        NOW));
        MockHttpSession session = authenticatedSession();

        mockMvc.perform(get("/drl/admin/quotas").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/quotas"))
                .andExpect(content().string(containsString("Live quota")))
                .andExpect(content().string(containsString("tenant-42")));
    }

    @Test
    void adminCssIsPublic() throws Exception {
        mockMvc.perform(get("/drl/admin/css/admin.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("--accent")));
    }

    private MockHttpSession authenticatedSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/drl/admin/login").param("apiKey", API_KEY).session(session))
                .andExpect(status().is3xxRedirection());
        return session;
    }

    private static RuleResponse sampleRule() {
        return new RuleResponse(
                1L,
                "tenant-42",
                "checkout",
                RateLimitAlgorithm.TOKEN_BUCKET,
                100,
                10.0,
                null,
                null,
                true,
                true,
                NOW,
                NOW);
    }
}
