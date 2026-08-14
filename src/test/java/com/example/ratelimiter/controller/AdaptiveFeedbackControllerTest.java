package com.example.ratelimiter.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ratelimiter.controller.dto.AdaptiveFeedbackRequest;
import com.example.ratelimiter.controller.dto.AdaptiveFeedbackResponse;
import com.example.ratelimiter.exception.ResourceNotFoundException;
import com.example.ratelimiter.service.AdaptiveFeedbackService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdaptiveFeedbackControllerTest {

    private static final String API_KEY = "test-api-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdaptiveFeedbackService adaptiveFeedbackService;

    @Test
    void feedbackReturns200() throws Exception {
        when(adaptiveFeedbackService.feedback(any(AdaptiveFeedbackRequest.class)))
                .thenReturn(new AdaptiveFeedbackResponse("tenant-42", "checkout", 0.55, 0.25, 120));

        mockMvc.perform(post("/api/v1/adaptive/feedback")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "identifier": "tenant-42",
                                  "namespace": "checkout",
                                  "downstreamErrorRate": 0.55
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adaptiveMultiplier").value(0.25))
                .andExpect(jsonPath("$.ttlSeconds").value(120));
    }

    @Test
    void feedbackMissingRuleReturns404() throws Exception {
        when(adaptiveFeedbackService.feedback(any(AdaptiveFeedbackRequest.class)))
                .thenThrow(new ResourceNotFoundException(
                        "No enabled rate limit rule for identifier and namespace"));

        mockMvc.perform(post("/api/v1/adaptive/feedback")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "identifier": "missing",
                                  "namespace": "ns",
                                  "downstreamErrorRate": 0.1
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("No enabled")));
    }

    @Test
    void feedbackInvalidRateReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/adaptive/feedback")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "identifier": "tenant-42",
                                  "namespace": "checkout",
                                  "downstreamErrorRate": 1.5
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void feedbackRequiresApiKey() throws Exception {
        mockMvc.perform(post("/api/v1/adaptive/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "identifier": "tenant-42",
                                  "namespace": "checkout",
                                  "downstreamErrorRate": 0.1
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }
}
