package com.example.ratelimiter.limiter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SlidingWindowEngineTest {

    /** Aligned to a 60s window boundary for predictable math. */
    private static final Instant WINDOW_START = Instant.parse("2026-08-14T07:00:00Z");

    @Test
    void allowsUntilLimitThenDenies() {
        MutableClock clock = new MutableClock(WINDOW_START);
        SlidingWindowEngine engine = new SlidingWindowEngine(3, 60, clock);

        assertThat(engine.tryConsume().allowed()).isTrue();
        assertThat(engine.tryConsume().allowed()).isTrue();
        RateLimitResult lastAllow = engine.tryConsume();
        assertThat(lastAllow.allowed()).isTrue();
        assertThat(lastAllow.remaining()).isEqualTo(0);

        RateLimitResult denied = engine.tryConsume();
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.remaining()).isEqualTo(0);
        assertThat(denied.resetAt()).isEqualTo(WINDOW_START.plusSeconds(60));
    }

    @Test
    void windowBoundaryDecaysPreviousCount() {
        MutableClock clock = new MutableClock(WINDOW_START);
        SlidingWindowEngine engine = new SlidingWindowEngine(5, 60, clock);

        for (int i = 0; i < 5; i++) {
            assertThat(engine.tryConsume().allowed()).isTrue();
        }
        assertThat(engine.tryConsume().allowed()).isFalse();

        // Just before next window: previous weight still ~1.0 → still denied.
        clock.set(WINDOW_START.plusSeconds(59));
        assertThat(engine.tryConsume().allowed()).isFalse();

        // At next window start: previous=5, current=0, weight=1.0 → estimated=5 → still deny for +1.
        clock.set(WINDOW_START.plusSeconds(60));
        assertThat(engine.tryConsume().allowed()).isFalse();

        // Mid-window: weight=0.5 → estimated=2.5 → room for 2 more (2.5+1<=5).
        clock.set(WINDOW_START.plusSeconds(90));
        RateLimitResult mid = engine.tryConsume();
        assertThat(mid.allowed()).isTrue();
        assertThat(mid.remaining()).isEqualTo(1); // floor(5 - 3.5) = 1
        assertThat(mid.resetAt()).isEqualTo(WINDOW_START.plusSeconds(120));
    }

    @Test
    void skippedWindowsClearPreviousCount() {
        MutableClock clock = new MutableClock(WINDOW_START);
        SlidingWindowEngine engine = new SlidingWindowEngine(2, 10, clock);

        assertThat(engine.tryConsume().allowed()).isTrue();
        assertThat(engine.tryConsume().allowed()).isTrue();
        assertThat(engine.tryConsume().allowed()).isFalse();

        // Jump more than one full window ahead — prior counts fall outside the horizon.
        clock.set(WINDOW_START.plusSeconds(30));
        RateLimitResult fresh = engine.tryConsume();
        assertThat(fresh.allowed()).isTrue();
        assertThat(fresh.remaining()).isEqualTo(1);
        assertThat(fresh.resetAt()).isEqualTo(WINDOW_START.plusSeconds(40));
    }

    @Test
    void remainingReflectsWeightedEstimate() {
        MutableClock clock = new MutableClock(WINDOW_START);
        SlidingWindowEngine engine = new SlidingWindowEngine(10, 100, clock);

        for (int i = 0; i < 10; i++) {
            engine.tryConsume();
        }

        clock.set(WINDOW_START.plusSeconds(100)); // new window; prev=10, weight=1 → estimated=10
        RateLimitResult atBoundary = engine.tryConsume();
        assertThat(atBoundary.allowed()).isFalse();
        assertThat(atBoundary.remaining()).isEqualTo(0);

        clock.set(WINDOW_START.plusSeconds(150)); // weight=0.5 → estimated=5
        RateLimitResult half = engine.tryConsume();
        assertThat(half.allowed()).isTrue();
        assertThat(half.remaining()).isEqualTo(4); // floor(10 - 6) = 4
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
