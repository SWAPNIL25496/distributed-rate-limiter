package com.example.ratelimiter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ratelimiter.controller.dto.AdaptiveFeedbackRequest;
import com.example.ratelimiter.controller.dto.AdaptiveFeedbackResponse;
import com.example.ratelimiter.domain.RateLimitAlgorithm;
import com.example.ratelimiter.exception.ResourceNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdaptiveFeedbackServiceTest {

    @Mock
    private RateLimitRuleService ruleService;

    @Mock
    private AdaptiveStateStore adaptiveStateStore;

    private AdaptiveFeedbackService service;

    @BeforeEach
    void setUp() {
        service = new AdaptiveFeedbackService(ruleService, adaptiveStateStore);
    }

    @Test
    void highErrorRateWritesQuarterMultiplier() {
        when(ruleService.resolveCached("tenant-42", "checkout"))
                .thenReturn(Optional.of(new RateLimitRuleService.ResolvedRule(rule(true), true)));

        AdaptiveFeedbackResponse response =
                service.feedback(new AdaptiveFeedbackRequest("tenant-42", "checkout", 0.55));

        assertThat(response.adaptiveMultiplier()).isEqualTo(0.25);
        assertThat(response.ttlSeconds()).isEqualTo(120);

        ArgumentCaptor<AdaptiveStateStore.AdaptiveState> captor =
                ArgumentCaptor.forClass(AdaptiveStateStore.AdaptiveState.class);
        verify(adaptiveStateStore).put(eq("tenant-42"), eq("checkout"), captor.capture());
        assertThat(captor.getValue().multiplier()).isEqualTo(0.25);
        assertThat(captor.getValue().errorRate()).isEqualTo(0.55);
    }

    @Test
    void missingRuleReturns404() {
        when(ruleService.resolveCached("x", "y")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.feedback(new AdaptiveFeedbackRequest("x", "y", 0.1)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(adaptiveStateStore, never()).put(any(), any(), any());
    }

    private static RuleCache.CachedRule rule(boolean enabled) {
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
                true);
    }
}
