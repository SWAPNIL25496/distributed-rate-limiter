package com.example.ratelimiter.service;

import com.example.ratelimiter.config.AppProperties;
import com.example.ratelimiter.controller.dto.EvaluateRequest;
import com.example.ratelimiter.controller.dto.EvaluateResponse;
import com.example.ratelimiter.domain.RateLimitAlgorithm;
import com.example.ratelimiter.exception.ResourceNotFoundException;
import com.example.ratelimiter.limiter.CounterKeys;
import com.example.ratelimiter.limiter.QuotaScriptExecutor;
import com.example.ratelimiter.limiter.RateLimitResult;
import com.example.ratelimiter.limiter.ShardSupport;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluateService {

    private static final Logger log = LoggerFactory.getLogger(EvaluateService.class);

    private final RateLimitRuleService ruleService;
    private final QuotaScriptExecutor quotaScriptExecutor;
    private final AdaptiveStateStore adaptiveStateStore;
    private final AppProperties appProperties;
    private final Clock clock;

    public EvaluateService(
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
    public EvaluateResponse evaluate(EvaluateRequest request) {
        String identifier = request.identifier().trim();
        String namespace = request.namespace().trim();

        RuleCache.CachedRule rule = ruleService
                .resolveCached(identifier, namespace)
                .map(RateLimitRuleService.ResolvedRule::rule)
                .filter(RuleCache.CachedRule::enabled)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No enabled rate limit rule for identifier and namespace"));

        double multiplier = resolveMultiplier(rule, identifier, namespace);
        int shards = appProperties.rateLimit().counterShards();
        int shardId = ShardSupport.selectShardId(shards);
        Instant now = clock.instant();

        RateLimitResult result = dispatch(rule, identifier, namespace, shardId, shards, multiplier, now);

        if (result.allowed()) {
            log.info(
                    "Evaluate allow identifier={} namespace={} algorithm={} remaining={} resetAt={} shardId={} multiplier={}",
                    identifier,
                    namespace,
                    rule.algorithm(),
                    result.remaining(),
                    result.resetAt(),
                    shardId,
                    multiplier);
        } else {
            log.info(
                    "Evaluate deny identifier={} namespace={} algorithm={} remaining={} resetAt={} shardId={} multiplier={}",
                    identifier,
                    namespace,
                    rule.algorithm(),
                    result.remaining(),
                    result.resetAt(),
                    shardId,
                    multiplier);
        }

        return new EvaluateResponse(result.allowed(), result.remaining(), result.resetAt(), rule.algorithm());
    }

    private double resolveMultiplier(RuleCache.CachedRule rule, String identifier, String namespace) {
        if (!rule.adaptiveEnabled()) {
            return 1.0;
        }
        return adaptiveStateStore
                .get(identifier, namespace)
                .map(AdaptiveStateStore.AdaptiveState::multiplier)
                .orElse(1.0);
    }

    private RateLimitResult dispatch(
            RuleCache.CachedRule rule,
            String identifier,
            String namespace,
            int shardId,
            int shards,
            double multiplier,
            Instant now) {
        if (rule.algorithm() == RateLimitAlgorithm.TOKEN_BUCKET) {
            int effectiveBurst = AdaptiveLimits.effectiveLimit(rule.burstCapacity(), multiplier);
            int burst = ShardSupport.shardLimit(effectiveBurst, shardId, shards);
            String key = CounterKeys.tokenBucket(identifier, namespace, shardId);
            return quotaScriptExecutor.evaluateTokenBucket(key, burst, rule.refillPerSecond(), now);
        }
        if (rule.algorithm() == RateLimitAlgorithm.SLIDING_WINDOW) {
            int effectiveLimit = AdaptiveLimits.effectiveLimit(rule.limit(), multiplier);
            int limit = ShardSupport.shardLimit(effectiveLimit, shardId, shards);
            String key = CounterKeys.slidingWindow(identifier, namespace, shardId);
            return quotaScriptExecutor.evaluateSlidingWindow(key, limit, rule.windowSeconds(), now);
        }
        throw new IllegalStateException("Unsupported algorithm: " + rule.algorithm());
    }
}
