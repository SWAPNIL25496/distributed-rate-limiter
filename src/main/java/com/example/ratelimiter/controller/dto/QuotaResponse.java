package com.example.ratelimiter.controller.dto;

import com.example.ratelimiter.domain.RateLimitAlgorithm;
import java.time.Instant;

/**
 * Observe response including adaptive fields (nullable when no adapt key / adaptive disabled).
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
            Integer effectiveLimit,
            Double adaptiveMultiplier,
            Double downstreamErrorRate,
            Instant resetAt) {
        return new QuotaResponse(
                identifier,
                namespace,
                algorithm,
                consumed,
                remaining,
                limit,
                effectiveLimit,
                adaptiveMultiplier,
                downstreamErrorRate,
                resetAt);
    }
}
