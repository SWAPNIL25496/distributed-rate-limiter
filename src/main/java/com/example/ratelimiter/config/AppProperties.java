package com.example.ratelimiter.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(

        @NotBlank(message = "app.api-key (env APP_API_KEY) must be configured")
        String apiKey,

        @Valid @DefaultValue RateLimit rateLimit) {

    public record RateLimit(@Min(1) @DefaultValue("1") int counterShards) {
    }
}
