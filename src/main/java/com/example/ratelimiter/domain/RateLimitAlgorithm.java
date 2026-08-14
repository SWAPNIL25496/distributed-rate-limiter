package com.example.ratelimiter.domain;

/**
 * Supported evaluate strategies for v1. Adaptive multipliers (Phase 8) scale effective
 * burst/limit but do not add algorithms.
 */
public enum RateLimitAlgorithm {
    TOKEN_BUCKET,
    SLIDING_WINDOW
}
