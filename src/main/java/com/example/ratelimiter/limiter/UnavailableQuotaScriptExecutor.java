package com.example.ratelimiter.limiter;

import java.time.Instant;

/**
 * Stand-in when Redis is unavailable (Surefire unit profile). Evaluate requires Redis Lua.
 */
public class UnavailableQuotaScriptExecutor implements QuotaScriptExecutor {

    @Override
    public RateLimitResult evaluateTokenBucket(
            String key, int burstCapacity, double refillPerSecond, Instant now) {
        throw new IllegalStateException("Redis Lua evaluate is unavailable in this profile");
    }

    @Override
    public RateLimitResult evaluateSlidingWindow(
            String key, int limit, int windowSeconds, Instant now) {
        throw new IllegalStateException("Redis Lua evaluate is unavailable in this profile");
    }
}
