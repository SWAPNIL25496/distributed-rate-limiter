package com.example.ratelimiter.config;

import com.example.ratelimiter.service.AdaptiveStateStore;
import com.example.ratelimiter.service.NoOpAdaptiveStateStore;
import com.example.ratelimiter.service.RedisAdaptiveStateStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class AdaptiveStateConfig {

    @Bean
    AdaptiveStateStore adaptiveStateStore(
            ObjectProvider<StringRedisTemplate> redisTemplate, ObjectMapper objectMapper) {
        StringRedisTemplate template = redisTemplate.getIfAvailable();
        if (template != null) {
            return new RedisAdaptiveStateStore(template, objectMapper);
        }
        return new NoOpAdaptiveStateStore();
    }
}
