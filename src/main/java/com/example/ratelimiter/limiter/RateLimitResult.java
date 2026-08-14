package com.example.ratelimiter.limiter;

import java.time.Instant;

/**
 * Outcome of a consume-one evaluation: admission decision, floored remaining quota, and reset time.
 */
public record RateLimitResult(boolean allowed, int remaining, Instant resetAt) {}
