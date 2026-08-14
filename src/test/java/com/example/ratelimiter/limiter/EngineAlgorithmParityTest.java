package com.example.ratelimiter.limiter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Engine-side parity: shared fixtures must match Phase 3 unit semantics. Full Lua parity is in
 * {@code EvaluateIT} (CI / {@code ./mvnw verify} with Docker).
 */
class EngineAlgorithmParityTest {

    @Test
    void tokenBucketFixturesMatchEngine() {
        for (AlgorithmParityFixtures.TokenBucketCase c : AlgorithmParityFixtures.tokenBucketCases()) {
            MutableClock clock = new MutableClock(AlgorithmParityFixtures.T0);
            TokenBucketEngine engine = new TokenBucketEngine(c.burst(), c.refillPerSecond(), clock);
            for (AlgorithmParityFixtures.TimedStep step : c.steps()) {
                Instant at = Instant.ofEpochMilli(step.atEpochMillis());
                clock.set(at);
                RateLimitResult result = engine.tryConsumeAt(at);
                assertThat(result.allowed())
                        .as("%s @ %s allowed", c.name(), at)
                        .isEqualTo(step.expectation().allowed());
                assertThat(result.remaining())
                        .as("%s @ %s remaining", c.name(), at)
                        .isEqualTo(step.expectation().remaining());
            }
        }
    }

    @Test
    void slidingWindowFixturesMatchEngine() {
        for (AlgorithmParityFixtures.SlidingWindowCase c :
                AlgorithmParityFixtures.slidingWindowCases()) {
            MutableClock clock = new MutableClock(AlgorithmParityFixtures.T0);
            SlidingWindowEngine engine = new SlidingWindowEngine(c.limit(), c.windowSeconds(), clock);
            for (AlgorithmParityFixtures.TimedStep step : c.steps()) {
                Instant at = Instant.ofEpochMilli(step.atEpochMillis());
                clock.set(at);
                RateLimitResult result = engine.tryConsumeAt(at);
                assertThat(result.allowed())
                        .as("%s @ %s allowed", c.name(), at)
                        .isEqualTo(step.expectation().allowed());
                assertThat(result.remaining())
                        .as("%s @ %s remaining", c.name(), at)
                        .isEqualTo(step.expectation().remaining());
            }
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
