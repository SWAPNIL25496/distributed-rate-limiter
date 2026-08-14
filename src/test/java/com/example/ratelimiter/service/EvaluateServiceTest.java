package com.example.ratelimiter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ratelimiter.config.AppProperties;
import com.example.ratelimiter.controller.dto.EvaluateRequest;
import com.example.ratelimiter.controller.dto.EvaluateResponse;
import com.example.ratelimiter.domain.RateLimitAlgorithm;
import com.example.ratelimiter.exception.ResourceNotFoundException;
import com.example.ratelimiter.limiter.QuotaScriptExecutor;
import com.example.ratelimiter.limiter.RateLimitResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvaluateServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");

    @Mock
    private RateLimitRuleService ruleService;

    @Mock
    private QuotaScriptExecutor quotaScriptExecutor;

    @Mock
    private AdaptiveStateStore adaptiveStateStore;

    private EvaluateService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties("test-key", new AppProperties.RateLimit(1));
        service = new EvaluateService(
                ruleService, quotaScriptExecutor, adaptiveStateStore, props, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void evaluateTokenBucketDispatchesLuaAndReturnsResponse() {
        when(ruleService.resolveCached("tenant-42", "checkout"))
                .thenReturn(Optional.of(new RateLimitRuleService.ResolvedRule(tokenBucketRule(true, true), true)));
        when(adaptiveStateStore.get("tenant-42", "checkout")).thenReturn(Optional.empty());
        when(quotaScriptExecutor.evaluateTokenBucket(
                        eq("rl:v1:tb:tenant-42:checkout:0"), eq(100), eq(10.0), eq(NOW)))
                .thenReturn(new RateLimitResult(true, 99, NOW.plusSeconds(1)));

        EvaluateResponse response = service.evaluate(new EvaluateRequest("tenant-42", "checkout"));

        assertThat(response.allowed()).isTrue();
        assertThat(response.remaining()).isEqualTo(99);
        assertThat(response.algorithm()).isEqualTo(RateLimitAlgorithm.TOKEN_BUCKET);
        assertThat(response.resetAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void evaluateAppliesAdaptiveMultiplierToBurst() {
        when(ruleService.resolveCached("tenant-42", "checkout"))
                .thenReturn(Optional.of(new RateLimitRuleService.ResolvedRule(tokenBucketRule(true, true), true)));
        when(adaptiveStateStore.get("tenant-42", "checkout"))
                .thenReturn(Optional.of(new AdaptiveStateStore.AdaptiveState(0.25, 0.6)));
        when(quotaScriptExecutor.evaluateTokenBucket(
                        eq("rl:v1:tb:tenant-42:checkout:0"), eq(25), eq(10.0), eq(NOW)))
                .thenReturn(new RateLimitResult(true, 24, NOW.plusSeconds(1)));

        EvaluateResponse response = service.evaluate(new EvaluateRequest("tenant-42", "checkout"));

        assertThat(response.allowed()).isTrue();
        verify(quotaScriptExecutor).evaluateTokenBucket(anyString(), eq(25), anyDouble(), any());
    }

    @Test
    void evaluateIgnoresAdaptKeyWhenAdaptiveDisabled() {
        when(ruleService.resolveCached("tenant-42", "checkout"))
                .thenReturn(Optional.of(new RateLimitRuleService.ResolvedRule(tokenBucketRule(true, false), true)));
        when(quotaScriptExecutor.evaluateTokenBucket(
                        eq("rl:v1:tb:tenant-42:checkout:0"), eq(100), eq(10.0), eq(NOW)))
                .thenReturn(new RateLimitResult(true, 99, NOW.plusSeconds(1)));

        service.evaluate(new EvaluateRequest("tenant-42", "checkout"));

        verify(adaptiveStateStore, never()).get(anyString(), anyString());
        verify(quotaScriptExecutor).evaluateTokenBucket(anyString(), eq(100), anyDouble(), any());
    }

    @Test
    void evaluateSlidingWindowDispatchesLua() {
        when(ruleService.resolveCached("tenant-42", "search"))
                .thenReturn(Optional.of(new RateLimitRuleService.ResolvedRule(slidingRule(true, true), false)));
        when(adaptiveStateStore.get("tenant-42", "search")).thenReturn(Optional.empty());
        when(quotaScriptExecutor.evaluateSlidingWindow(
                        eq("rl:v1:sw:tenant-42:search:0"), eq(1000), eq(60), eq(NOW)))
                .thenReturn(new RateLimitResult(false, 0, NOW.plusSeconds(60)));

        EvaluateResponse response = service.evaluate(new EvaluateRequest("tenant-42", "search"));

        assertThat(response.allowed()).isFalse();
        assertThat(response.remaining()).isZero();
        assertThat(response.algorithm()).isEqualTo(RateLimitAlgorithm.SLIDING_WINDOW);
        verify(quotaScriptExecutor)
                .evaluateSlidingWindow(anyString(), anyInt(), anyInt(), any(Instant.class));
    }

    @Test
    void missingRuleReturns404() {
        when(ruleService.resolveCached("x", "y")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.evaluate(new EvaluateRequest("x", "y")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No enabled");
    }

    @Test
    void disabledRuleReturns404() {
        when(ruleService.resolveCached("tenant-42", "checkout"))
                .thenReturn(Optional.of(new RateLimitRuleService.ResolvedRule(tokenBucketRule(false, true), true)));

        assertThatThrownBy(() -> service.evaluate(new EvaluateRequest("tenant-42", "checkout")))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(quotaScriptExecutor, never())
                .evaluateTokenBucket(anyString(), anyInt(), anyDouble(), any());
    }

    private static RuleCache.CachedRule tokenBucketRule(boolean enabled, boolean adaptiveEnabled) {
        return new RuleCache.CachedRule(
                1L,
                "tenant-42",
                "checkout",
                RateLimitAlgorithm.TOKEN_BUCKET,
                100,
                10.0,
                null,
                null,
                enabled,
                adaptiveEnabled);
    }

    private static RuleCache.CachedRule slidingRule(boolean enabled, boolean adaptiveEnabled) {
        return new RuleCache.CachedRule(
                2L,
                "tenant-42",
                "search",
                RateLimitAlgorithm.SLIDING_WINDOW,
                null,
                null,
                1000,
                60,
                enabled,
                adaptiveEnabled);
    }
}
