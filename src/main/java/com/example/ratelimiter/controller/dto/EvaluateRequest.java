package com.example.ratelimiter.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EvaluateRequest(
        @NotBlank @Size(max = 255) String identifier,
        @NotBlank @Size(max = 255) String namespace) {
}
