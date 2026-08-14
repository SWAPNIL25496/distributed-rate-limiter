package com.example.ratelimiter.limiter;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Counter-based sliding-window engine: at most {@code limit} admits in any rolling
 * {@code windowSeconds}, approximated with previous + current fixed sub-windows weighted by
 * elapsed fraction (not a full request log).
 *
 * <p>{@code resetAt} is the end of the current fixed sub-window (when the previous-window weight
 * drops to zero for the next sub-window).
 */
public final class SlidingWindowEngine implements RateLimitAlgorithm {

    private final int limit;
    private final long windowSeconds;
    private final Clock clock;

    /** Epoch-second start of the current fixed sub-window. */
    private long windowStartEpochSec;
    private long previousCount;
    private long currentCount;

    public SlidingWindowEngine(int limit, int windowSeconds, Clock clock) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        if (windowSeconds < 1) {
            throw new IllegalArgumentException("windowSeconds must be >= 1");
        }
        this.limit = limit;
        this.windowSeconds = windowSeconds;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.windowStartEpochSec = alignedWindowStart(clock.instant().getEpochSecond());
        this.previousCount = 0L;
        this.currentCount = 0L;
    }

    /**
     * Restores engine state (for Redis round-trip / Phase 4 parity).
     */
    public SlidingWindowEngine(
            int limit,
            int windowSeconds,
            Clock clock,
            long windowStartEpochSec,
            long previousCount,
            long currentCount) {
        this(limit, windowSeconds, clock);
        this.windowStartEpochSec = windowStartEpochSec;
        this.previousCount = Math.max(0L, previousCount);
        this.currentCount = Math.max(0L, currentCount);
    }

    public int limit() {
        return limit;
    }

    public int windowSeconds() {
        return (int) windowSeconds;
    }

    public long windowStartEpochSec() {
        return windowStartEpochSec;
    }

    public long previousCount() {
        return previousCount;
    }

    public long currentCount() {
        return currentCount;
    }

    @Override
    public RateLimitResult tryConsume() {
        return tryConsumeAt(clock.instant());
    }

    /**
     * Evaluate at an explicit instant (tests / parity).
     */
    public RateLimitResult tryConsumeAt(Instant now) {
        Objects.requireNonNull(now, "now");
        rollWindows(now.getEpochSecond());
        double estimated = estimatedCount(now.getEpochSecond());
        boolean allowed = estimated + 1.0 <= limit;
        if (allowed) {
            currentCount++;
        }
        double after = estimatedCount(now.getEpochSecond());
        int remaining = (int) Math.max(0L, (long) Math.floor(limit - after));
        return new RateLimitResult(allowed, remaining, resetAt());
    }

    private void rollWindows(long nowEpochSec) {
        long aligned = alignedWindowStart(nowEpochSec);
        if (aligned == windowStartEpochSec) {
            return;
        }
        if (aligned == windowStartEpochSec + windowSeconds) {
            previousCount = currentCount;
        } else {
            // Skipped one or more full windows — prior activity is outside the rolling horizon.
            previousCount = 0L;
        }
        currentCount = 0L;
        windowStartEpochSec = aligned;
    }

    private double estimatedCount(long nowEpochSec) {
        double elapsedInWindow = nowEpochSec - windowStartEpochSec;
        double weight = 1.0 - (elapsedInWindow / (double) windowSeconds);
        if (weight < 0.0) {
            weight = 0.0;
        }
        if (weight > 1.0) {
            weight = 1.0;
        }
        return previousCount * weight + currentCount;
    }

    private Instant resetAt() {
        return Instant.ofEpochSecond(windowStartEpochSec + windowSeconds);
    }

    private long alignedWindowStart(long epochSec) {
        return epochSec - (epochSec % windowSeconds);
    }
}
