package com.example.ratelimiter.controller.dto;

public record AdaptiveFeedbackResponse(
        String identifier,
        String namespace,
        double downstreamErrorRate,
        double adaptiveMultiplier,
        int ttlSeconds) {
}
