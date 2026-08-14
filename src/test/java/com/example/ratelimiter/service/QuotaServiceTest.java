package com.example.ratelimiter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.ratelimiter.config.AppProperties;
import com.example.ratelimiter.controller.dto.QuotaResponse;
import com.example.ratelimiter.domain.RateLimitAlgorithm;
import com.example.ratelimiter.limiter.ObserveResult;
import com.example.ratelimiter.limiter.QuotaScriptExecutor;
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
class QuotaServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");

    @Mock
    private RateLimitRuleService ruleService;

    @Mock
    private QuotaScriptExecutor quotaScriptExecutor;

    @Mock
    private AdaptiveStateStore adaptiveStateStore;

    private QuotaService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties("test-key", new AppProperties.RateLimit(1));
        service = new QuotaService(
                ruleService, quotaScriptExecutor, adaptiveStateStore, props, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void observeWithoutAdaptKeyLeavesAdaptiveFieldsNull() {
        when(ruleService.resolveCached("tenant-42", "checkout"))
                .thenReturn(Optional.of(new RateLimitRuleService.ResolvedRule(rule(true), true)));
        when(adaptiveStateStore.get("tenant-42", "checkout")).thenReturn(Optional.empty());
        when(quotaScriptExecutor.observeTokenBucket(
                        eq("rl:v1:tb:tenant-42:checkout:0"), eq(100), eq(10.0), eq(NOW)))
                .thenReturn(new ObserveResult(17, NOW.plusSeconds(1), 83));

        QuotaResponse response = service.observe("tenant-42", "checkout");

        assertThat(response.limit()).isEqualTo(100);
        assertThat(response.remaining()).isEqualTo(17);
        assertThat(response.effectiveLimit()).isNull();
        assertThat(response.adaptiveMultiplier()).isNull();
        assertThat(response.downstreamErrorRate()).isNull();
    }

    @Test
    void observeWithAdaptKeyIncludesAdaptiveFields() {
        when(ruleService.resolveCached("tenant-42", "checkout"))
                .thenReturn(Optional.of(new RateLimitRuleService.ResolvedRule(rule(true), true)));
        when(adaptiveStateStore.get("tenant-42", "checkout"))
                .thenReturn(Optional.of(new AdaptiveStateStore.AdaptiveState(0.5, 0.25)));
        when(quotaScriptExecutor.observeTokenBucket(
                        eq("rl:v1:tb:tenant-42:checkout:0"), eq(50), eq(10.0), eq(NOW)))
                .thenReturn(new ObserveResult(10, NOW.plusSeconds(1), 40));

        QuotaResponse response = service.observe("tenant-42", "checkout");

        assertThat(response.limit()).isEqualTo(100);
        assertThat(response.effectiveLimit()).isEqualTo(50);
        assertThat(response.adaptiveMultiplier()).isEqualTo(0.5);
        assertThat(response.downstreamErrorRate()).isEqualTo(0.25);
        assertThat(response.remaining()).isEqualTo(10);
    }

    @Test
    void observeIgnoresAdaptKeyWhenAdaptiveDisabled() {
        when(ruleService.resolveCached("tenant-42", "checkout"))
                .thenReturn(Optional.of(new RateLimitRuleService.ResolvedRule(rule(false), true)));
        when(quotaScriptExecutor.observeTokenBucket(
                        eq("rl:v1:tb:tenant-42:checkout:0"), eq(100), eq(10.0), eq(NOW)))
                .thenReturn(new ObserveResult(100, NOW.plusSeconds(1), 0));

        QuotaResponse response = service.observe("tenant-42", "checkout");

        assertThat(response.effectiveLimit()).isNull();
        assertThat(response.adaptiveMultiplier()).isNull();
        assertThat(response.downstreamErrorRate()).isNull();
    }

    private static RuleCache.CachedRule rule(boolean adaptiveEnabled) {
        return new RuleCache.CachedRule(
                1L,
                "tenant-42",
                "checkout",
                RateLimitAlgorithm.TOKEN_BUCKET,
                100,
                10.0,
                null,
                null,
                true,
                adaptiveEnabled);
    }
}
