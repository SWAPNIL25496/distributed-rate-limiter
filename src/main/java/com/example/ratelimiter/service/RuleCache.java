package com.example.ratelimiter.service;

import com.example.ratelimiter.domain.RateLimitAlgorithm;
import com.example.ratelimiter.domain.RateLimitRule;
import java.util.Optional;

/**
 * Redis-backed rule cache (ADR 0002). Write-through on CRUD; cache-aside helper for evaluate.
 */
public interface RuleCache {

    void put(RateLimitRule rule);

    void evict(String identifier, String namespace);

    Optional<CachedRule> get(String identifier, String namespace);

    record CachedRule(
            Long id,
            String identifier,
            String namespace,
            RateLimitAlgorithm algorithm,
            Integer burstCapacity,
            Double refillPerSecond,
            Integer limit,
            Integer windowSeconds,
            boolean enabled,
            boolean adaptiveEnabled) {

        public static CachedRule from(RateLimitRule rule) {
            return new CachedRule(
                    rule.getId(),
                    rule.getIdentifier(),
                    rule.getNamespace(),
                    rule.getAlgorithm(),
                    rule.getBurstCapacity(),
                    rule.getRefillPerSecond(),
                    rule.getLimitCount(),
                    rule.getWindowSeconds(),
                    rule.isEnabled(),
                    rule.isAdaptiveEnabled());
        }
    }
}
