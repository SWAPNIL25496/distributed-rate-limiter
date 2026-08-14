package com.example.ratelimiter.limiter;

import java.time.Instant;

/**
 * Read-only quota snapshot for one shard key: floored remaining, reset time, and consumed
 * relative to the per-shard limit ({@code limit - remaining}).
 */
public record ObserveResult(int remaining, Instant resetAt, int consumed) {}
