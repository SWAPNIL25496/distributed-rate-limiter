package com.example.ratelimiter.service;

import com.example.ratelimiter.limiter.CounterKeys;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

public class RedisAdaptiveStateStore implements AdaptiveStateStore {

    static final Duration TTL = Duration.ofSeconds(AdaptiveLimits.TTL_SECONDS);

    private static final Logger log = LoggerFactory.getLogger(RedisAdaptiveStateStore.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisAdaptiveStateStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void put(String identifier, String namespace, AdaptiveState state) {
        String key = CounterKeys.adaptive(identifier, namespace);
        try {
            String json = objectMapper.writeValueAsString(state);
            redisTemplate.opsForValue().set(key, json, TTL);
            log.debug("Wrote adaptive state key={} multiplier={}", key, state.multiplier());
        } catch (Exception ex) {
            log.warn("Failed to write adaptive state key={}: {}", key, ex.toString());
            throw new IllegalStateException("Failed to write adaptive state", ex);
        }
    }

    @Override
    public Optional<AdaptiveState> get(String identifier, String namespace) {
        String key = CounterKeys.adaptive(identifier, namespace);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, AdaptiveState.class));
        } catch (Exception ex) {
            log.warn("Failed to read adaptive state key={}: {}", key, ex.toString());
            return Optional.empty();
        }
    }
}
