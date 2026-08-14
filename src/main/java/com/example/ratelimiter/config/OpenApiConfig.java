package com.example.ratelimiter.config;

import com.example.ratelimiter.security.ApiKeyAuthFilter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String API_KEY_SCHEME = "ApiKeyAuth";

    @Bean
    OpenAPI rateLimiterOpenApi() {
        SecurityScheme apiKeyScheme = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(ApiKeyAuthFilter.API_KEY_HEADER);

        return new OpenAPI()
                .info(new Info()
                        .title("Distributed Rate Limiter API")
                        .version("v1")
                        .description("Evaluate, observe and configure per-tenant rate limit rules."))
                .components(new Components().addSecuritySchemes(API_KEY_SCHEME, apiKeyScheme))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME));
    }
}
