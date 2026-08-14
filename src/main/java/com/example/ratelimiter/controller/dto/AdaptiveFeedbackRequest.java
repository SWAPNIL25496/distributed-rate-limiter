package com.example.ratelimiter.controller.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdaptiveFeedbackRequest(
        @NotBlank @Size(max = 255) String identifier,
        @NotBlank @Size(max = 255) String namespace,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double downstreamErrorRate) {
}
