package com.example.ratelimiter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ratelimiter.domain.RateLimitAlgorithm;
import com.redis.testcontainers.RedisContainer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Concurrent evaluate contention + observe consistency against real Postgres/Redis. Failsafe /
 * CI only ({@code ./mvnw verify}); Surefire stays Docker-free.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ConcurrentEvaluateIT {

    private static final String API_KEY = "integration-test-key";
    private static final int LIMIT = 50;
    private static final int PARALLEL = 100;

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

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void concurrentEvaluatesDoNotOverAdmitBeyondBurst() throws Exception {
        String identifier = "contention-" + UUID.randomUUID();
        insertTokenBucketRule(identifier, "checkout", LIMIT, 0.0001);

        ExecutorService pool = Executors.newFixedThreadPool(32);
        AtomicInteger allows = new AtomicInteger();
        try {
            List<Callable<Void>> tasks = new ArrayList<>(PARALLEL);
            for (int i = 0; i < PARALLEL; i++) {
                tasks.add(() -> {
                    MvcResult result = mockMvc.perform(post("/api/v1/evaluate")
                                    .header("X-API-Key", API_KEY)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(evaluateBody(identifier, "checkout")))
                            .andExpect(status().isOk())
                            .andReturn();
                    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
                    if (body.get("allowed").asBoolean()) {
                        allows.incrementAndGet();
                    }
                    return null;
                });
            }
            List<Future<Void>> futures = pool.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(allows.get())
                .as("total allows under contention must be ≤ burst limit")
                .isLessThanOrEqualTo(LIMIT)
                .isEqualTo(LIMIT);
    }

    @Test
    void observeMatchesPriorEvaluatesWithoutConsuming() throws Exception {
        String identifier = "observe-" + UUID.randomUUID();
        insertTokenBucketRule(identifier, "checkout", 5, 1.0);

        mockMvc.perform(get("/api/v1/quotas/" + identifier)
                        .param("namespace", "checkout")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consumed").value(0))
                .andExpect(jsonPath("$.remaining").value(5))
                .andExpect(jsonPath("$.limit").value(5));

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/evaluate")
                            .header("X-API-Key", API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(evaluateBody(identifier, "checkout")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.allowed").value(true));
        }

        mockMvc.perform(get("/api/v1/quotas/" + identifier)
                        .param("namespace", "checkout")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consumed").value(3))
                .andExpect(jsonPath("$.remaining").value(2))
                .andExpect(jsonPath("$.limit").value(5))
                .andExpect(jsonPath("$.algorithm").value("TOKEN_BUCKET"));

        // Second observe must not consume further.
        mockMvc.perform(get("/api/v1/quotas/" + identifier)
                        .param("namespace", "checkout")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consumed").value(3))
                .andExpect(jsonPath("$.remaining").value(2));
    }

    @Test
    void observeMissingRuleReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/quotas/no-such")
                        .param("namespace", "ns")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isNotFound());
    }

    private void insertTokenBucketRule(
            String identifier, String namespace, int burst, double refill) {
        jdbcClient
                .sql(
                        """
                        INSERT INTO rate_limit_rules
                          (identifier, namespace, algorithm, burst_capacity, refill_per_second, enabled)
                        VALUES (?, ?, ?, ?, ?, TRUE)
                        """)
                .param(identifier)
                .param(namespace)
                .param(RateLimitAlgorithm.TOKEN_BUCKET.name())
                .param(burst)
                .param(refill)
                .update();
    }

    private static String evaluateBody(String identifier, String namespace) {
        return """
                { "identifier": "%s", "namespace": "%s" }
                """
                .formatted(identifier, namespace);
    }
}
