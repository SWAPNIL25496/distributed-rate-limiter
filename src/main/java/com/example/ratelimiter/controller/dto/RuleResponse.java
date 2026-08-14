package com.example.ratelimiter.controller.dto;

import com.example.ratelimiter.domain.RateLimitAlgorithm;
import com.example.ratelimiter.domain.RateLimitRule;
import java.time.Instant;

public record RuleResponse(
        Long id,
        String identifier,
        String namespace,
        RateLimitAlgorithm algorithm,
        Integer burstCapacity,
        Double refillPerSecond,
        Integer limit,
        Integer windowSeconds,
        boolean enabled,
        boolean adaptiveEnabled,
        Instant createdAt,
        Instant updatedAt) {

    public static RuleResponse from(RateLimitRule rule) {
        return new RuleResponse(
                rule.getId(),
                rule.getIdentifier(),
                rule.getNamespace(),
                rule.getAlgorithm(),
                rule.getBurstCapacity(),
                rule.getRefillPerSecond(),
                rule.getLimitCount(),
                rule.getWindowSeconds(),
                rule.isEnabled(),
                rule.isAdaptiveEnabled(),
                rule.getCreatedAt(),
                rule.getUpdatedAt());
    }
}
