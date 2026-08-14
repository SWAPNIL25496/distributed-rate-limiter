package com.example.ratelimiter.config;

import com.example.ratelimiter.limiter.QuotaScriptExecutor;
import com.example.ratelimiter.limiter.RedisQuotaScriptExecutor;
import com.example.ratelimiter.limiter.UnavailableQuotaScriptExecutor;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class LuaScriptConfig {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    QuotaScriptExecutor redisQuotaScriptExecutor(StringRedisTemplate redisTemplate) {
        return new RedisQuotaScriptExecutor(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(QuotaScriptExecutor.class)
    QuotaScriptExecutor unavailableQuotaScriptExecutor() {
        return new UnavailableQuotaScriptExecutor();
    }
}
