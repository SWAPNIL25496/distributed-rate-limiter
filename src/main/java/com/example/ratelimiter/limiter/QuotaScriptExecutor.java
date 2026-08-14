package com.example.ratelimiter.limiter;

import java.time.Instant;

/**
 * Production evaluate/observe path against Redis Lua (ADR 0004). Pure {@link RateLimitAlgorithm}
 * engines mirror the same math for unit / parity tests.
 */
public interface QuotaScriptExecutor {

    RateLimitResult evaluateTokenBucket(
            String key, int burstCapacity, double refillPerSecond, Instant now);

    RateLimitResult evaluateSlidingWindow(String key, int limit, int windowSeconds, Instant now);

    /** Read-only token-bucket snapshot; never consumes. */
    ObserveResult observeTokenBucket(
            String key, int burstCapacity, double refillPerSecond, Instant now);

    /** Read-only sliding-window snapshot; never consumes. */
    ObserveResult observeSlidingWindow(String key, int limit, int windowSeconds, Instant now);
}
