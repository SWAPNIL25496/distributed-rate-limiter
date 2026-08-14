package com.example.ratelimiter.config;

import com.example.ratelimiter.service.NoOpRuleCache;
import com.example.ratelimiter.service.RedisRuleCache;
import com.example.ratelimiter.service.RuleCache;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class RuleCacheConfig {

    @Bean
    RuleCache ruleCache(ObjectProvider<StringRedisTemplate> redisTemplate, ObjectMapper objectMapper) {
        StringRedisTemplate template = redisTemplate.getIfAvailable();
        if (template != null) {
            return new RedisRuleCache(template, objectMapper);
        }
        return new NoOpRuleCache();
    }
}
