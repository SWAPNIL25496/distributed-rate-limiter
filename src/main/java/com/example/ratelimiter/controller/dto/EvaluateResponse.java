package com.example.ratelimiter.controller.dto;

import com.example.ratelimiter.domain.RateLimitAlgorithm;
import java.time.Instant;

public record EvaluateResponse(
        boolean allowed, int remaining, Instant resetAt, RateLimitAlgorithm algorithm) {
}
