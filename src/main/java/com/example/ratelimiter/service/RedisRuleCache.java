package com.example.ratelimiter.service;

import com.example.ratelimiter.domain.RateLimitRule;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

public class RedisRuleCache implements RuleCache {

    static final String KEY_PREFIX = "rl:v1:rule:";
    static final Duration TTL = Duration.ofSeconds(60);

    private static final Logger log = LoggerFactory.getLogger(RedisRuleCache.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisRuleCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void put(RateLimitRule rule) {
        String key = key(rule.getIdentifier(), rule.getNamespace());
        try {
            String json = objectMapper.writeValueAsString(CachedRule.from(rule));
            redisTemplate.opsForValue().set(key, json, TTL);
            log.debug("Wrote rule cache key={}", key);
        } catch (Exception ex) {
            log.warn("Failed to write rule cache key={}: {}", key, ex.toString());
        }
    }

    @Override
    public void evict(String identifier, String namespace) {
        String key = key(identifier, namespace);
        try {
            redisTemplate.delete(key);
            log.debug("Evicted rule cache key={}", key);
        } catch (Exception ex) {
            log.warn("Failed to evict rule cache key={}: {}", key, ex.toString());
        }
    }

    @Override
    public Optional<CachedRule> get(String identifier, String namespace) {
        String key = key(identifier, namespace);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, CachedRule.class));
        } catch (Exception ex) {
            log.warn("Failed to read rule cache key={}: {}", key, ex.toString());
            return Optional.empty();
        }
    }

    static String key(String identifier, String namespace) {
        return KEY_PREFIX + identifier + ":" + namespace;
    }
}
