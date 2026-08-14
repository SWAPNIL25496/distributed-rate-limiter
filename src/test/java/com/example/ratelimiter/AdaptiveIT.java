package com.example.ratelimiter;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.redis.testcontainers.RedisContainer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Adaptive feedback → evaluate/observe path. Failsafe / CI only ({@code ./mvnw verify}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdaptiveIT {

    private static final String API_KEY = "integration-test-key";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("ratelimiter");

    @Container
    static final RedisContainer REDIS = new RedisContainer("redis:7");

    @DynamicPropertySource
    static void datastoreConnectionEnv(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getRedisHost);
        registry.add("spring.data.redis.port", REDIS::getRedisPort);
        registry.add("app.api-key", () -> API_KEY);
        registry.add("app.rate-limit.counter-shards", () -> "1");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void feedbackTightensObserveEffectiveLimit() throws Exception {
        String identifier = "adapt-" + UUID.randomUUID();
        insertTokenBucketRule(identifier, "checkout", 100, 10.0, true);

        mockMvc.perform(post("/api/v1/adaptive/feedback")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "identifier": "%s",
                                  "namespace": "checkout",
                                  "downstreamErrorRate": 0.55
                                }
                                """
                                        .formatted(identifier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adaptiveMultiplier").value(0.25));

        mockMvc.perform(get("/api/v1/quotas/" + identifier)
                        .param("namespace", "checkout")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(100))
                .andExpect(jsonPath("$.effectiveLimit").value(25))
                .andExpect(jsonPath("$.adaptiveMultiplier").value(0.25))
                .andExpect(jsonPath("$.downstreamErrorRate").value(0.55));
    }

    @Test
    void feedbackWithoutRuleReturns404() throws Exception {
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

    private void insertTokenBucketRule(
            String identifier, String namespace, int burst, double refill, boolean adaptiveEnabled) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO rate_limit_rules
                          (identifier, namespace, algorithm, burst_capacity, refill_per_second,
                           enabled, adaptive_enabled, created_at, updated_at)
                        VALUES (?, ?, 'TOKEN_BUCKET', ?, ?, TRUE, ?, NOW(), NOW())
                        """)
                .params(identifier, namespace, burst, refill, adaptiveEnabled)
                .update();
    }
}
