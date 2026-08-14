package com.example.ratelimiter.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ratelimiter.controller.dto.RuleRequest;
import com.example.ratelimiter.controller.dto.RuleResponse;
import com.example.ratelimiter.domain.RateLimitAlgorithm;
import com.example.ratelimiter.exception.BadRequestException;
import com.example.ratelimiter.exception.ConflictException;
import com.example.ratelimiter.exception.ResourceNotFoundException;
import com.example.ratelimiter.service.RateLimitRuleService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MockMvc CRUD coverage without Postgres/Redis. The Surefire profile excludes datastore
 * auto-config; {@link RateLimitRuleService} is replaced with a mock so the controller + filter
 * + exception advice still exercise the HTTP contract.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RuleControllerTest {

    private static final String API_KEY = "test-api-key";
    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RateLimitRuleService ruleService;

    @Test
    void createTokenBucketReturns201() throws Exception {
        when(ruleService.create(any(RuleRequest.class)))
                .thenReturn(tokenBucketResponse(1L));

        mockMvc.perform(post("/api/v1/rules")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "identifier": "tenant-42",
                                  "namespace": "checkout",
                                  "algorithm": "TOKEN_BUCKET",
                                  "burstCapacity": 100,
                                  "refillPerSecond": 10.0,
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.algorithm").value("TOKEN_BUCKET"))
                .andExpect(jsonPath("$.burstCapacity").value(100))
                .andExpect(jsonPath("$.refillPerSecond").value(10.0));
    }

    @Test
    void createDuplicateReturns409() throws Exception {
        when(ruleService.create(any(RuleRequest.class)))
                .thenThrow(new ConflictException("Rule already exists for identifier and namespace"));

        mockMvc.perform(post("/api/v1/rules")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "identifier": "tenant-42",
                                  "namespace": "checkout",
                                  "algorithm": "TOKEN_BUCKET",
                                  "burstCapacity": 100,
                                  "refillPerSecond": 10.0
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
    }

    @Test
    void createMissingIdentifierReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/rules")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "identifier": "",
                                  "namespace": "checkout",
                                  "algorithm": "TOKEN_BUCKET",
                                  "burstCapacity": 100,
                                  "refillPerSecond": 10.0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createTokenBucketWithoutBurstReturns400FromService() throws Exception {
        when(ruleService.create(any(RuleRequest.class)))
                .thenThrow(new BadRequestException("TOKEN_BUCKET requires burstCapacity >= 1"));

        mockMvc.perform(post("/api/v1/rules")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "identifier": "tenant-42",
                                  "namespace": "checkout",
                                  "algorithm": "TOKEN_BUCKET",
                                  "refillPerSecond": 10.0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("burstCapacity")));
    }

    @Test
    void listReturnsRules() throws Exception {
        when(ruleService.list(50)).thenReturn(List.of(tokenBucketResponse(1L), slidingWindowResponse(2L)));

        mockMvc.perform(get("/api/v1/rules").header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].algorithm").value("SLIDING_WINDOW"))
                .andExpect(jsonPath("$[1].limit").value(1000));
    }

    @Test
    void listRejectsLimitAboveMax() throws Exception {
        mockMvc.perform(get("/api/v1/rules").param("limit", "201").header("X-API-Key", API_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("limit")));
    }

    @Test
    void getByIdReturns200() throws Exception {
        when(ruleService.get(7L)).thenReturn(tokenBucketResponse(7L));

        mockMvc.perform(get("/api/v1/rules/7").header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void getMissingReturns404() throws Exception {
        when(ruleService.get(99L)).thenThrow(new ResourceNotFoundException("Rule not found: 99"));

        mockMvc.perform(get("/api/v1/rules/99").header("X-API-Key", API_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void updateReturns200() throws Exception {
        when(ruleService.update(eq(1L), any(RuleRequest.class))).thenReturn(tokenBucketResponse(1L));

        mockMvc.perform(put("/api/v1/rules/1")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "identifier": "tenant-42",
                                  "namespace": "checkout",
                                  "algorithm": "TOKEN_BUCKET",
                                  "burstCapacity": 50,
                                  "refillPerSecond": 5.0,
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/rules/1").header("X-API-Key", API_KEY))
                .andExpect(status().isNoContent());

        verify(ruleService).delete(1L);
    }

    @Test
    void deleteMissingReturns404() throws Exception {
        doThrow(new ResourceNotFoundException("Rule not found: 1")).when(ruleService).delete(1L);

        mockMvc.perform(delete("/api/v1/rules/1").header("X-API-Key", API_KEY))
                .andExpect(status().isNotFound());
    }

    private static RuleResponse tokenBucketResponse(Long id) {
        return new RuleResponse(
                id,
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

    private static RuleResponse slidingWindowResponse(Long id) {
        return new RuleResponse(
                id,
                "tenant-42",
                "search",
                RateLimitAlgorithm.SLIDING_WINDOW,
                null,
                null,
                1000,
                60,
                true,
                true,
                NOW,
                NOW);
    }
}
