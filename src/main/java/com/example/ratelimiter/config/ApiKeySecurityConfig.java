package com.example.ratelimiter.config;

import com.example.ratelimiter.security.ApiKeyAuthFilter;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class ApiKeySecurityConfig {

    static final List<String> PROTECTED_PATTERNS = List.of("/api/v1/**");

    static final List<String> PUBLIC_PATTERNS = List.of(
            "/actuator/health/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**");

    @Bean
    FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilter(AppProperties appProperties, ObjectMapper objectMapper) {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(
                appProperties.apiKey(), PROTECTED_PATTERNS, PUBLIC_PATTERNS, objectMapper);
        FilterRegistrationBean<ApiKeyAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
