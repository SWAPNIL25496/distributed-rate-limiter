package com.example.ratelimiter.config;

import com.example.ratelimiter.limiter.QuotaScriptExecutor;
import com.example.ratelimiter.limiter.RedisQuotaScriptExecutor;
import com.example.ratelimiter.limiter.UnavailableQuotaScriptExecutor;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Prefer Redis Lua when {@link StringRedisTemplate} exists. Avoid {@code @ConditionalOnBean}
 * here — user {@code @Configuration} is processed before Redis auto-config, so that condition
 * falsely picks the unavailable stub and breaks Failsafe ITs / production.
 */
@Configuration
public class LuaScriptConfig {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    QuotaScriptExecutor quotaScriptExecutor(ObjectProvider<StringRedisTemplate> redisTemplate) {
        StringRedisTemplate template = redisTemplate.getIfAvailable();
        if (template != null) {
            return new RedisQuotaScriptExecutor(template);
        }
        return new UnavailableQuotaScriptExecutor();
    }
}
