package com.example.ratelimiter.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.example.ratelimiter.domain.RateLimitRule;
import com.example.ratelimiter.repository.RateLimitRuleRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Surefire profile excludes JPA auto-config (no Docker). Provide a repository stub so the
 * application context — including {@code RuleController} — still starts for MockMvc tests.
 * Integration coverage uses real Postgres via {@code *IT} / Failsafe.
 */
@Configuration
@Profile("test")
public class TestRepositoryConfig {

    @Bean
    RateLimitRuleRepository rateLimitRuleRepository() {
        RateLimitRuleRepository repository = mock(RateLimitRuleRepository.class);
        lenient().when(repository.findAll(any(Pageable.class))).thenReturn(Page.<RateLimitRule>empty());
        return repository;
    }
}
