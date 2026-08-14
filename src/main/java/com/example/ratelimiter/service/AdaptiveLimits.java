package com.example.ratelimiter.service;

/**
 * Locked adaptive multiplier mapping and effective-limit math ([ADR 0008](docs/adr/0008-adaptive-limits.md)).
 */
public final class AdaptiveLimits {

    public static final int TTL_SECONDS = 120;

    private AdaptiveLimits() {}

    /**
     * Maps downstream error rate (0.0–1.0) to a temporary limit multiplier.
     *
     * <ul>
     *   <li>{@code >= 0.5} → 0.25×
     *   <li>{@code >= 0.2} → 0.5×
     *   <li>else → 1.0×
     * </ul>
     */
    public static double multiplierFor(double downstreamErrorRate) {
        if (downstreamErrorRate < 0.0 || downstreamErrorRate > 1.0) {
            throw new IllegalArgumentException("downstreamErrorRate must be between 0.0 and 1.0");
        }
        if (downstreamErrorRate >= 0.5) {
            return 0.25;
        }
        if (downstreamErrorRate >= 0.2) {
            return 0.5;
        }
        return 1.0;
    }

    /**
     * Applies multiplier to a base burst/limit. Minimum effective limit is 1 when base ≥ 1.
     */
    public static int effectiveLimit(int baseLimit, double multiplier) {
        if (baseLimit < 1) {
            throw new IllegalArgumentException("baseLimit must be >= 1");
        }
        if (multiplier <= 0.0) {
            throw new IllegalArgumentException("multiplier must be > 0");
        }
        return Math.max(1, (int) Math.floor(baseLimit * multiplier));
    }
}
