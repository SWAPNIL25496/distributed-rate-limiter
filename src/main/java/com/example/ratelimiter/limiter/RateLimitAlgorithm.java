package com.example.ratelimiter.limiter;

/**
 * Pluggable consume-one rate-limit strategy (token bucket or sliding window).
 *
 * <p>Distinct from {@link com.example.ratelimiter.domain.RateLimitAlgorithm}, which is the persisted
 * rule discriminator. Phase 4 maps that enum to these pure engines / Lua adapters.
 */
public interface RateLimitAlgorithm {

    /**
     * Attempt to consume one unit of quota at the engine's clock time.
     *
     * @return allow/deny with floored remaining and algorithm-specific {@code resetAt}
     */
    RateLimitResult tryConsume();
}
