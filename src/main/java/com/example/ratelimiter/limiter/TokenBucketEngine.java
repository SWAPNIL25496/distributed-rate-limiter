package com.example.ratelimiter.limiter;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Pure token-bucket engine: burst up to {@code burstCapacity}, refill at {@code refillPerSecond}
 * tokens/sec, consume one token when allowed. Remaining floors fractional tokens. {@code resetAt}
 * is when the bucket would reach full capacity with no further consumption.
 */
public final class TokenBucketEngine implements RateLimitAlgorithm {

    private final int burstCapacity;
    private final double refillPerSecond;
    private final Clock clock;

    private double tokens;
    private long lastRefillEpochMillis;

    public TokenBucketEngine(int burstCapacity, double refillPerSecond, Clock clock) {
        if (burstCapacity < 1) {
            throw new IllegalArgumentException("burstCapacity must be >= 1");
        }
        if (refillPerSecond <= 0.0) {
            throw new IllegalArgumentException("refillPerSecond must be > 0");
        }
        this.burstCapacity = burstCapacity;
        this.refillPerSecond = refillPerSecond;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.tokens = burstCapacity;
        this.lastRefillEpochMillis = clock.millis();
    }

    /**
     * Restores engine state (for Redis round-trip / Phase 4 parity).
     */
    public TokenBucketEngine(
            int burstCapacity,
            double refillPerSecond,
            Clock clock,
            double tokens,
            long lastRefillEpochMillis) {
        this(burstCapacity, refillPerSecond, clock);
        this.tokens = Math.min(burstCapacity, Math.max(0.0, tokens));
        this.lastRefillEpochMillis = lastRefillEpochMillis;
    }

    public int burstCapacity() {
        return burstCapacity;
    }

    public double refillPerSecond() {
        return refillPerSecond;
    }

    public double tokens() {
        return tokens;
    }

    public long lastRefillEpochMillis() {
        return lastRefillEpochMillis;
    }

    @Override
    public RateLimitResult tryConsume() {
        return tryConsumeAt(clock.instant());
    }

    /**
     * Evaluate at an explicit instant (tests / parity). Updates refill using the given time.
     */
    public RateLimitResult tryConsumeAt(Instant now) {
        Objects.requireNonNull(now, "now");
        refill(now);
        boolean allowed = tokens >= 1.0;
        if (allowed) {
            tokens -= 1.0;
        }
        int remaining = (int) Math.floor(tokens);
        return new RateLimitResult(allowed, remaining, resetAt(now));
    }

    private void refill(Instant now) {
        long nowMillis = now.toEpochMilli();
        long elapsedMillis = nowMillis - lastRefillEpochMillis;
        if (elapsedMillis <= 0) {
            return;
        }
        double elapsedSeconds = elapsedMillis / 1000.0;
        tokens = Math.min(burstCapacity, tokens + elapsedSeconds * refillPerSecond);
        lastRefillEpochMillis = nowMillis;
    }

    private Instant resetAt(Instant now) {
        double deficit = burstCapacity - tokens;
        if (deficit <= 0.0) {
            return now;
        }
        double secondsToFull = deficit / refillPerSecond;
        long nanos = Math.round(secondsToFull * 1_000_000_000L);
        return now.plusNanos(nanos);
    }
}
