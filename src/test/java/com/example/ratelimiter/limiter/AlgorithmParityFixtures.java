package com.example.ratelimiter.limiter;

import java.time.Instant;
import java.util.List;

/**
 * Shared allow/deny + remaining scenarios for Java engines (Surefire) and Lua path (Failsafe IT).
 */
public final class AlgorithmParityFixtures {

    public static final Instant T0 = Instant.parse("2026-08-14T07:00:00Z");

    private AlgorithmParityFixtures() {}

    public record Expectation(boolean allowed, int remaining) {}

    public record TimedStep(long atEpochMillis, Expectation expectation) {}

    public record TokenBucketCase(
            String name, int burst, double refillPerSecond, List<TimedStep> steps) {}

    public record SlidingWindowCase(
            String name, int limit, int windowSeconds, List<TimedStep> steps) {}

    public static List<TokenBucketCase> tokenBucketCases() {
        return List.of(
                new TokenBucketCase(
                        "burst_exhaust",
                        3,
                        1.0,
                        List.of(
                                step(T0, true, 2),
                                step(T0, true, 1),
                                step(T0, true, 0),
                                step(T0, false, 0))),
                new TokenBucketCase(
                        "refill_one_per_second",
                        2,
                        1.0,
                        List.of(
                                step(T0, true, 1),
                                step(T0, true, 0),
                                step(T0, false, 0),
                                step(T0.plusSeconds(1), true, 0),
                                step(T0.plusSeconds(1), false, 0),
                                step(T0.plusSeconds(2), true, 0))),
                new TokenBucketCase(
                        "fractional_floor",
                        1,
                        0.5,
                        List.of(
                                step(T0, true, 0),
                                step(T0, false, 0),
                                step(T0.plusMillis(1500), false, 0),
                                step(T0.plusMillis(2000), true, 0))));
    }

    public static List<SlidingWindowCase> slidingWindowCases() {
        Instant start = T0;
        return List.of(
                new SlidingWindowCase(
                        "limit_exhaust",
                        3,
                        60,
                        List.of(
                                step(start, true, 2),
                                step(start, true, 1),
                                step(start, true, 0),
                                step(start, false, 0))),
                new SlidingWindowCase(
                        "window_boundary",
                        5,
                        60,
                        List.of(
                                step(start, true, 4),
                                step(start, true, 3),
                                step(start, true, 2),
                                step(start, true, 1),
                                step(start, true, 0),
                                step(start, false, 0),
                                step(start.plusSeconds(59), false, 0),
                                step(start.plusSeconds(60), false, 0),
                                step(start.plusSeconds(90), true, 1))),
                new SlidingWindowCase(
                        "skipped_windows",
                        2,
                        10,
                        List.of(
                                step(start, true, 1),
                                step(start, true, 0),
                                step(start, false, 0),
                                step(start.plusSeconds(30), true, 1))));
    }

    private static TimedStep step(Instant at, boolean allowed, int remaining) {
        return new TimedStep(at.toEpochMilli(), new Expectation(allowed, remaining));
    }
}
