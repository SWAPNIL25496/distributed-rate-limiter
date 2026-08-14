package com.example.ratelimiter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ratelimiter.domain.RateLimitAlgorithm;
import com.example.ratelimiter.limiter.AlgorithmParityFixtures;
import com.example.ratelimiter.limiter.CounterKeys;
import com.example.ratelimiter.limiter.QuotaScriptExecutor;
import com.example.ratelimiter.limiter.RateLimitResult;
import com.example.ratelimiter.limiter.SlidingWindowEngine;
import com.example.ratelimiter.limiter.TokenBucketEngine;
import com.redis.testcontainers.RedisContainer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
 * Evaluate API + Java↔Lua parity against real Postgres/Redis. Failsafe / CI only ({@code
 * ./mvnw verify}); Surefire stays Docker-free.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EvaluateIT {

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

    @Autowired
    private QuotaScriptExecutor quotaScriptExecutor;

    @Test
    void evaluateTokenBucketAllowsThenDeniesOverHttp() throws Exception {
        String identifier = "tb-" + UUID.randomUUID();
        insertTokenBucketRule(identifier, "checkout", 2, 1.0);

        mockMvc.perform(post("/api/v1/evaluate")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evaluateBody(identifier, "checkout")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.remaining").value(1))
                .andExpect(jsonPath("$.algorithm").value("TOKEN_BUCKET"));

        mockMvc.perform(post("/api/v1/evaluate")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evaluateBody(identifier, "checkout")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.remaining").value(0));

        mockMvc.perform(post("/api/v1/evaluate")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evaluateBody(identifier, "checkout")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.remaining").value(0));
    }

    @Test
    void evaluateMissingRuleReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/evaluate")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evaluateBody("no-such-tenant", "no-such-ns")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("No enabled")));
    }

    @Test
    void evaluateDisabledRuleReturns404() throws Exception {
        String identifier = "disabled-" + UUID.randomUUID();
        jdbcClient
                .sql(
                        """
                        INSERT INTO rate_limit_rules
                          (identifier, namespace, algorithm, burst_capacity, refill_per_second, enabled)
                        VALUES (?, ?, ?, ?, ?, FALSE)
                        """)
                .param(identifier)
                .param("checkout")
                .param(RateLimitAlgorithm.TOKEN_BUCKET.name())
                .param(10)
                .param(1.0)
                .update();

        mockMvc.perform(post("/api/v1/evaluate")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evaluateBody(identifier, "checkout")))
                .andExpect(status().isNotFound());
    }

    @Test
    void luaTokenBucketParityMatchesJavaEngine() {
        for (AlgorithmParityFixtures.TokenBucketCase c : AlgorithmParityFixtures.tokenBucketCases()) {
            String key = CounterKeys.tokenBucket("parity-tb-" + c.name(), "ns", 0);
            Clock clock = Clock.fixed(AlgorithmParityFixtures.T0, ZoneOffset.UTC);
            TokenBucketEngine engine = new TokenBucketEngine(c.burst(), c.refillPerSecond(), clock);

            for (AlgorithmParityFixtures.TimedStep step : c.steps()) {
                Instant at = Instant.ofEpochMilli(step.atEpochMillis());
                RateLimitResult javaResult = engine.tryConsumeAt(at);
                RateLimitResult luaResult = quotaScriptExecutor.evaluateTokenBucket(
                        key, c.burst(), c.refillPerSecond(), at);

                assertThat(luaResult.allowed())
                        .as("TB %s @ %s allowed", c.name(), at)
                        .isEqualTo(javaResult.allowed())
                        .isEqualTo(step.expectation().allowed());
                assertThat(luaResult.remaining())
                        .as("TB %s @ %s remaining", c.name(), at)
                        .isEqualTo(javaResult.remaining())
                        .isEqualTo(step.expectation().remaining());
            }
        }
    }

    @Test
    void luaSlidingWindowParityMatchesJavaEngine() {
        for (AlgorithmParityFixtures.SlidingWindowCase c :
                AlgorithmParityFixtures.slidingWindowCases()) {
            String key = CounterKeys.slidingWindow("parity-sw-" + c.name(), "ns", 0);
            Clock clock = Clock.fixed(AlgorithmParityFixtures.T0, ZoneOffset.UTC);
            SlidingWindowEngine engine = new SlidingWindowEngine(c.limit(), c.windowSeconds(), clock);

            for (AlgorithmParityFixtures.TimedStep step : c.steps()) {
                Instant at = Instant.ofEpochMilli(step.atEpochMillis());
                RateLimitResult javaResult = engine.tryConsumeAt(at);
                RateLimitResult luaResult = quotaScriptExecutor.evaluateSlidingWindow(
                        key, c.limit(), c.windowSeconds(), at);

                assertThat(luaResult.allowed())
                        .as("SW %s @ %s allowed", c.name(), at)
                        .isEqualTo(javaResult.allowed())
                        .isEqualTo(step.expectation().allowed());
                assertThat(luaResult.remaining())
                        .as("SW %s @ %s remaining", c.name(), at)
                        .isEqualTo(javaResult.remaining())
                        .isEqualTo(step.expectation().remaining());
            }
        }
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
