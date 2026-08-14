package com.example.ratelimiter.config;

import com.example.ratelimiter.service.NoOpRuleCache;
import com.example.ratelimiter.service.RedisRuleCache;
import com.example.ratelimiter.service.RuleCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class RuleCacheConfig {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    RuleCache redisRuleCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        return new RedisRuleCache(redisTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(RuleCache.class)
    RuleCache noOpRuleCache() {
        return new NoOpRuleCache();
    }
}
