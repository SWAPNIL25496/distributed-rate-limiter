package com.example.ratelimiter.service;

import com.example.ratelimiter.domain.RateLimitRule;
import java.util.Optional;

/**
 * Stand-in when Redis is not available (Surefire unit profile).
 */
public class NoOpRuleCache implements RuleCache {

    @Override
    public void put(RateLimitRule rule) {
        // no-op
    }

    @Override
    public void evict(String identifier, String namespace) {
        // no-op
    }

    @Override
    public Optional<CachedRule> get(String identifier, String namespace) {
        return Optional.empty();
    }
}
