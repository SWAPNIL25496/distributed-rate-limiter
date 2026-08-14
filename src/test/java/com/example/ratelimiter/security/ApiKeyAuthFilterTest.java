package com.example.ratelimiter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ratelimiter.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the registered filter through the real servlet chain. Phase 1 has no
 * {@code /api/v1} handler yet, so pass-through is asserted as "anything but 401".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiKeyAuthFilterTest {

    private static final String PROTECTED_PATH = "/api/v1/rules";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppProperties appProperties;

    @Test
    void missingApiKeyIsRejected() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").value(containsString("X-API-Key")))
                .andExpect(jsonPath("$.path").value(PROTECTED_PATH));
    }

    @Test
    void invalidApiKeyIsRejected() throws Exception {
        mockMvc.perform(post(PROTECTED_PATH).header(ApiKeyAuthFilter.API_KEY_HEADER, "not-the-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validApiKeyPassesThroughTheFilter() throws Exception {
        int actualStatus = mockMvc
                .perform(get(PROTECTED_PATH).header(ApiKeyAuthFilter.API_KEY_HEADER, appProperties.apiKey()))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(actualStatus).isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/v3/api-docs", "/swagger-ui.html", "/drl/admin"})
    void publicAndNotYetProtectedPathsAreNeverChallenged(String path) throws Exception {
        int actualStatus = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();

        assertThat(actualStatus).isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
    }
}
