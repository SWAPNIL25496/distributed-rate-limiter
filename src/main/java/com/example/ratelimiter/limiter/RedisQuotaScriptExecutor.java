package com.example.ratelimiter.limiter;

import java.time.Instant;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * Executes classpath Lua evaluate scripts via Redis {@code EVAL}/{@code EVALSHA}. One
 * {@code KEYS[1]} per call.
 */
public class RedisQuotaScriptExecutor implements QuotaScriptExecutor {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> tokenBucketScript;
    private final DefaultRedisScript<List> slidingWindowScript;

    public RedisQuotaScriptExecutor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = load("lua/token_bucket_evaluate.lua");
        this.slidingWindowScript = load("lua/sliding_window_evaluate.lua");
    }

    @Override
    public RateLimitResult evaluateTokenBucket(
            String key, int burstCapacity, double refillPerSecond, Instant now) {
        @SuppressWarnings("unchecked")
        List<Long> raw = (List<Long>) redisTemplate.execute(
                tokenBucketScript,
                List.of(key),
                String.valueOf(now.toEpochMilli()),
                String.valueOf(burstCapacity),
                String.valueOf(refillPerSecond));
        return toResult(raw);
    }

    @Override
    public RateLimitResult evaluateSlidingWindow(
            String key, int limit, int windowSeconds, Instant now) {
        @SuppressWarnings("unchecked")
        List<Long> raw = (List<Long>) redisTemplate.execute(
                slidingWindowScript,
                List.of(key),
                String.valueOf(now.getEpochSecond()),
                String.valueOf(limit),
                String.valueOf(windowSeconds));
        return toResult(raw);
    }

    DefaultRedisScript<List> tokenBucketScript() {
        return tokenBucketScript;
    }

    DefaultRedisScript<List> slidingWindowScript() {
        return slidingWindowScript;
    }

    private static RateLimitResult toResult(List<Long> raw) {
        if (raw == null || raw.size() < 3) {
            throw new IllegalStateException("Lua evaluate returned unexpected result: " + raw);
        }
        boolean allowed = number(raw.get(0)) == 1L;
        int remaining = (int) number(raw.get(1));
        Instant resetAt = Instant.ofEpochMilli(number(raw.get(2)));
        return new RateLimitResult(allowed, remaining, resetAt);
    }

    private static long number(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static DefaultRedisScript<List> load(String classpathLocation) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setResultType(List.class);
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(classpathLocation)));
        return script;
    }
}
