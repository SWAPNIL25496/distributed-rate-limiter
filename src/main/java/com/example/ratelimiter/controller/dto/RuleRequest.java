package com.example.ratelimiter.controller.dto;

import com.example.ratelimiter.domain.RateLimitAlgorithm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RuleRequest(
        @NotBlank @Size(max = 255) String identifier,
        @NotBlank @Size(max = 255) String namespace,
        @NotNull RateLimitAlgorithm algorithm,
        Integer burstCapacity,
        Double refillPerSecond,
        Integer limit,
        Integer windowSeconds,
        Boolean enabled) {
}
