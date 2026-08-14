package com.example.ratelimiter.config;

import com.example.ratelimiter.service.AdaptiveStateStore;
import com.example.ratelimiter.service.NoOpAdaptiveStateStore;
import com.example.ratelimiter.service.RedisAdaptiveStateStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class AdaptiveStateConfig {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    AdaptiveStateStore redisAdaptiveStateStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        return new RedisAdaptiveStateStore(redisTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(AdaptiveStateStore.class)
    AdaptiveStateStore noOpAdaptiveStateStore() {
        return new NoOpAdaptiveStateStore();
    }
}
