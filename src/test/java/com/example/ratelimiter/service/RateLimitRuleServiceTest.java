package com.example.ratelimiter.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ratelimiter.controller.dto.RuleRequest;
import com.example.ratelimiter.domain.RateLimitAlgorithm;
import com.example.ratelimiter.domain.RateLimitRule;
import com.example.ratelimiter.exception.BadRequestException;
import com.example.ratelimiter.exception.ConflictException;
import com.example.ratelimiter.repository.RateLimitRuleRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RateLimitRuleServiceTest {

    @Mock
    private RateLimitRuleRepository repository;

    @Mock
    private RuleCache ruleCache;

    private RateLimitRuleService service;

    @BeforeEach
    void setUp() {
        service = new RateLimitRuleService(repository, ruleCache);
    }

    @Test
    void createTokenBucketPersistsAndWriteThrough() {
        when(repository.existsByIdentifierAndNamespace("tenant-42", "checkout")).thenReturn(false);
        when(repository.save(any(RateLimitRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(new RuleRequest(
                "tenant-42", "checkout", RateLimitAlgorithm.TOKEN_BUCKET, 100, 10.0, null, null, true, true));

        ArgumentCaptor<RateLimitRule> captor = ArgumentCaptor.forClass(RateLimitRule.class);
        verify(repository).save(captor.capture());
        verify(ruleCache).put(captor.getValue());
    }

    @Test
    void createRejectsDuplicatePair() {
        when(repository.existsByIdentifierAndNamespace("tenant-42", "checkout")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new RuleRequest(
                        "tenant-42",
                        "checkout",
                        RateLimitAlgorithm.TOKEN_BUCKET,
                        100,
                        10.0,
                        null,
                        null,
                        true,
                        true)))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void createTokenBucketRequiresBurstAndRefill() {
        assertThatThrownBy(() -> service.create(new RuleRequest(
                        "t", "n", RateLimitAlgorithm.TOKEN_BUCKET, null, 10.0, null, null, true, true)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("burstCapacity");

        assertThatThrownBy(() -> service.create(new RuleRequest(
                        "t", "n", RateLimitAlgorithm.TOKEN_BUCKET, 10, 0.0, null, null, true, true)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("refillPerSecond");
    }

    @Test
    void createSlidingWindowRequiresLimitAndWindow() {
        assertThatThrownBy(() -> service.create(new RuleRequest(
                        "t", "n", RateLimitAlgorithm.SLIDING_WINDOW, null, null, null, 60, true, true)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("limit");

        assertThatThrownBy(() -> service.create(new RuleRequest(
                        "t", "n", RateLimitAlgorithm.SLIDING_WINDOW, null, null, 100, 0, true, true)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("windowSeconds");
    }

    @Test
    void updateDisableEvictsCache() {
        RateLimitRule existing = new RateLimitRule();
        existing.setIdentifier("tenant-42");
        existing.setNamespace("checkout");
        existing.setAlgorithm(RateLimitAlgorithm.TOKEN_BUCKET);
        existing.setBurstCapacity(100);
        existing.setRefillPerSecond(10.0);
        existing.setEnabled(true);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByIdentifierAndNamespaceAndIdNot("tenant-42", "checkout", 1L))
                .thenReturn(false);
        when(repository.save(any(RateLimitRule.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(
                1L,
                new RuleRequest(
                        "tenant-42",
                        "checkout",
                        RateLimitAlgorithm.TOKEN_BUCKET,
                        100,
                        10.0,
                        null,
                        null,
                        false,
                        true));

        verify(ruleCache).evict("tenant-42", "checkout");
        verify(ruleCache, never()).put(any());
    }
}
