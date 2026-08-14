package com.example.ratelimiter.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ratelimiter.controller.dto.EvaluateRequest;
import com.example.ratelimiter.controller.dto.EvaluateResponse;
import com.example.ratelimiter.domain.RateLimitAlgorithm;
import com.example.ratelimiter.exception.ResourceNotFoundException;
import com.example.ratelimiter.service.EvaluateService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MockMvc evaluate contract without Redis/Lua. {@link EvaluateService} is mocked so Surefire
 * stays Docker-free.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EvaluateControllerTest {

    private static final String API_KEY = "test-api-key";
    private static final Instant RESET = Instant.parse("2026-08-14T12:00:30Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EvaluateService evaluateService;

    @Test
    void evaluateAllowReturns200() throws Exception {
        when(evaluateService.evaluate(any(EvaluateRequest.class)))
                .thenReturn(new EvaluateResponse(true, 17, RESET, RateLimitAlgorithm.TOKEN_BUCKET));

        mockMvc.perform(post("/api/v1/evaluate")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "identifier": "tenant-42", "namespace": "checkout" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.remaining").value(17))
                .andExpect(jsonPath("$.resetAt").value("2026-08-14T12:00:30Z"))
                .andExpect(jsonPath("$.algorithm").value("TOKEN_BUCKET"));
    }

    @Test
    void evaluateDenyReturns200() throws Exception {
        when(evaluateService.evaluate(any(EvaluateRequest.class)))
                .thenReturn(new EvaluateResponse(false, 0, RESET, RateLimitAlgorithm.SLIDING_WINDOW));

        mockMvc.perform(post("/api/v1/evaluate")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "identifier": "tenant-42", "namespace": "search" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.remaining").value(0))
                .andExpect(jsonPath("$.algorithm").value("SLIDING_WINDOW"));
    }

    @Test
    void evaluateMissingRuleReturns404() throws Exception {
        when(evaluateService.evaluate(any(EvaluateRequest.class)))
                .thenThrow(new ResourceNotFoundException(
                        "No enabled rate limit rule for identifier and namespace"));

        mockMvc.perform(post("/api/v1/evaluate")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "identifier": "missing", "namespace": "ns" }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("No enabled")));
    }

    @Test
    void evaluateBlankIdentifierReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/evaluate")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "identifier": "", "namespace": "checkout" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void evaluateRequiresApiKey() throws Exception {
        mockMvc.perform(post("/api/v1/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "identifier": "tenant-42", "namespace": "checkout" }
                                """))
                .andExpect(status().isUnauthorized());
    }
}
