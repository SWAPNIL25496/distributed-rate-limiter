package com.example.ratelimiter.controller.dto;

import com.example.ratelimiter.domain.RateLimitAlgorithm;
import java.time.Instant;

/**
 * Observe response. Adaptive fields are nullable until Phase 8 (omit or null OK).
 */
public record QuotaResponse(
        String identifier,
        String namespace,
        RateLimitAlgorithm algorithm,
        int consumed,
        int remaining,
        int limit,
        Integer effectiveLimit,
        Double adaptiveMultiplier,
        Double downstreamErrorRate,
        Instant resetAt) {

    public static QuotaResponse of(
            String identifier,
            String namespace,
            RateLimitAlgorithm algorithm,
            int consumed,
            int remaining,
            int limit,
            Instant resetAt) {
        return new QuotaResponse(
                identifier,
                namespace,
                algorithm,
                consumed,
                remaining,
                limit,
                null,
                null,
                null,
                resetAt);
    }
}
