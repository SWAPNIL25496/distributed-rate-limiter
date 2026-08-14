package com.example.ratelimiter.service;

import com.example.ratelimiter.config.AppProperties;
import com.example.ratelimiter.controller.dto.QuotaResponse;
import com.example.ratelimiter.domain.RateLimitAlgorithm;
import com.example.ratelimiter.exception.BadRequestException;
import com.example.ratelimiter.exception.ResourceNotFoundException;
import com.example.ratelimiter.limiter.CounterKeys;
import com.example.ratelimiter.limiter.ObserveResult;
import com.example.ratelimiter.limiter.QuotaScriptExecutor;
import com.example.ratelimiter.limiter.ShardSupport;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuotaService {

    private static final Logger log = LoggerFactory.getLogger(QuotaService.class);

    private final RateLimitRuleService ruleService;
    private final QuotaScriptExecutor quotaScriptExecutor;
    private final AdaptiveStateStore adaptiveStateStore;
    private final AppProperties appProperties;
    private final Clock clock;

    public QuotaService(
            RateLimitRuleService ruleService,
            QuotaScriptExecutor quotaScriptExecutor,
            AdaptiveStateStore adaptiveStateStore,
            AppProperties appProperties,
            Clock clock) {
        this.ruleService = ruleService;
        this.quotaScriptExecutor = quotaScriptExecutor;
        this.adaptiveStateStore = adaptiveStateStore;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public QuotaResponse observe(String identifier, String namespace) {
        String id = requireNonBlank(identifier, "identifier");
        String ns = requireNonBlank(namespace, "namespace");

        RuleCache.CachedRule rule = ruleService
                .resolveCached(id, ns)
                .map(RateLimitRuleService.ResolvedRule::rule)
                .filter(RuleCache.CachedRule::enabled)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No enabled rate limit rule for identifier and namespace"));

        int shards = appProperties.rateLimit().counterShards();
        int baseLimit = baseLimit(rule);
        AdaptiveView adaptive = resolveAdaptive(rule, id, ns, baseLimit);
        int observeLimit = adaptive.effectiveLimit() != null ? adaptive.effectiveLimit() : baseLimit;
        Instant now = clock.instant();

        int totalRemaining = 0;
        int totalConsumed = 0;
        Instant resetAt = null;

        for (int shardId = 0; shardId < shards; shardId++) {
            int shardLimit = ShardSupport.shardLimit(observeLimit, shardId, shards);
            ObserveResult shard = observeShard(rule, id, ns, shardId, shardLimit, now);
            totalRemaining += shard.remaining();
            totalConsumed += shard.consumed();
            if (resetAt == null || shard.resetAt().isBefore(resetAt)) {
                resetAt = shard.resetAt();
            }
        }

        log.info(
                "Observe identifier={} namespace={} algorithm={} consumed={} remaining={} limit={} effectiveLimit={}",
                id,
                ns,
                rule.algorithm(),
                totalConsumed,
                totalRemaining,
                baseLimit,
                adaptive.effectiveLimit());

        return QuotaResponse.of(
                id,
                ns,
                rule.algorithm(),
                totalConsumed,
                totalRemaining,
                baseLimit,
                adaptive.effectiveLimit(),
                adaptive.multiplier(),
                adaptive.errorRate(),
                resetAt);
    }

    private AdaptiveView resolveAdaptive(
            RuleCache.CachedRule rule, String identifier, String namespace, int baseLimit) {
        if (!rule.adaptiveEnabled()) {
            return AdaptiveView.absent();
        }
        Optional<AdaptiveStateStore.AdaptiveState> state = adaptiveStateStore.get(identifier, namespace);
        if (state.isEmpty()) {
            return AdaptiveView.absent();
        }
        AdaptiveStateStore.AdaptiveState adapt = state.get();
        return new AdaptiveView(
                adapt.multiplier(),
                adapt.errorRate(),
                AdaptiveLimits.effectiveLimit(baseLimit, adapt.multiplier()));
    }

    private ObserveResult observeShard(
            RuleCache.CachedRule rule,
            String identifier,
            String namespace,
            int shardId,
            int shardLimit,
            Instant now) {
        if (rule.algorithm() == RateLimitAlgorithm.TOKEN_BUCKET) {
            String key = CounterKeys.tokenBucket(identifier, namespace, shardId);
            return quotaScriptExecutor.observeTokenBucket(
                    key, shardLimit, rule.refillPerSecond(), now);
        }
        if (rule.algorithm() == RateLimitAlgorithm.SLIDING_WINDOW) {
            String key = CounterKeys.slidingWindow(identifier, namespace, shardId);
            return quotaScriptExecutor.observeSlidingWindow(
                    key, shardLimit, rule.windowSeconds(), now);
        }
        throw new IllegalStateException("Unsupported algorithm: " + rule.algorithm());
    }

    private static int baseLimit(RuleCache.CachedRule rule) {
        if (rule.algorithm() == RateLimitAlgorithm.TOKEN_BUCKET) {
            return rule.burstCapacity();
        }
        if (rule.algorithm() == RateLimitAlgorithm.SLIDING_WINDOW) {
            return rule.limit();
        }
        throw new IllegalStateException("Unsupported algorithm: " + rule.algorithm());
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(field + " is required");
        }
        return value.trim();
    }

    private record AdaptiveView(Double multiplier, Double errorRate, Integer effectiveLimit) {
        static AdaptiveView absent() {
            return new AdaptiveView(null, null, null);
        }
    }
}
