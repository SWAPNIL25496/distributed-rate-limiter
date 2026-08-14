package com.example.ratelimiter.limiter;

import java.time.Instant;

/**
 * Production evaluate path against Redis Lua (ADR 0004). Pure {@link RateLimitAlgorithm} engines
 * mirror the same math for unit / parity tests.
 */
public interface QuotaScriptExecutor {

    RateLimitResult evaluateTokenBucket(
            String key, int burstCapacity, double refillPerSecond, Instant now);

    RateLimitResult evaluateSlidingWindow(String key, int limit, int windowSeconds, Instant now);
}
