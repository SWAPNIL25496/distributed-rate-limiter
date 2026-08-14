package com.example.ratelimiter.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ratelimiter.controller.dto.QuotaResponse;
import com.example.ratelimiter.domain.RateLimitAlgorithm;
import com.example.ratelimiter.exception.ResourceNotFoundException;
import com.example.ratelimiter.service.QuotaService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MockMvc observe contract without Redis/Lua. {@link QuotaService} is mocked so Surefire stays
 * Docker-free.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuotaControllerTest {

    private static final String API_KEY = "test-api-key";
    private static final Instant RESET = Instant.parse("2026-08-14T12:00:30Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuotaService quotaService;

    @Test
    void observeReturns200() throws Exception {
        when(quotaService.observe(eq("tenant-42"), eq("checkout")))
                .thenReturn(QuotaResponse.of(
                        "tenant-42",
                        "checkout",
                        RateLimitAlgorithm.TOKEN_BUCKET,
                        83,
                        17,
                        100,
                        null,
                        null,
                        null,
                        RESET));

        mockMvc.perform(get("/api/v1/quotas/tenant-42")
                        .param("namespace", "checkout")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identifier").value("tenant-42"))
                .andExpect(jsonPath("$.namespace").value("checkout"))
                .andExpect(jsonPath("$.algorithm").value("TOKEN_BUCKET"))
                .andExpect(jsonPath("$.consumed").value(83))
                .andExpect(jsonPath("$.remaining").value(17))
                .andExpect(jsonPath("$.limit").value(100))
                .andExpect(jsonPath("$.resetAt").value("2026-08-14T12:00:30Z"));
    }

    @Test
    void observeMissingNamespaceReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/quotas/tenant-42").header("X-API-Key", API_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("namespace")));
    }

    @Test
    void observeBlankNamespaceReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/quotas/tenant-42")
                        .param("namespace", "  ")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("namespace")));
    }

    @Test
    void observeMissingRuleReturns404() throws Exception {
        when(quotaService.observe(eq("missing"), eq("ns")))
                .thenThrow(new ResourceNotFoundException(
                        "No enabled rate limit rule for identifier and namespace"));

        mockMvc.perform(get("/api/v1/quotas/missing")
                        .param("namespace", "ns")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("No enabled")));
    }

    @Test
    void observeRequiresApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/quotas/tenant-42").param("namespace", "checkout"))
                .andExpect(status().isUnauthorized());
    }
}
