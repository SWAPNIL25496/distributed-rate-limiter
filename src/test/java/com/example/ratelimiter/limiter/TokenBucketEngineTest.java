package com.example.ratelimiter.limiter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TokenBucketEngineTest {

    private static final Instant T0 = Instant.parse("2026-08-14T07:00:00Z");

    @Test
    void allowsUntilBurstExhaustedThenDenies() {
        MutableClock clock = new MutableClock(T0);
        TokenBucketEngine engine = new TokenBucketEngine(3, 1.0, clock);

        RateLimitResult first = engine.tryConsume();
        assertThat(first.allowed()).isTrue();
        assertThat(first.remaining()).isEqualTo(2);

        assertThat(engine.tryConsume().allowed()).isTrue();
        RateLimitResult lastAllow = engine.tryConsume();
        assertThat(lastAllow.allowed()).isTrue();
        assertThat(lastAllow.remaining()).isEqualTo(0);

        RateLimitResult denied = engine.tryConsume();
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.remaining()).isEqualTo(0);
    }

    @Test
    void burstThenSustainRefillsOneTokenPerSecond() {
        MutableClock clock = new MutableClock(T0);
        TokenBucketEngine engine = new TokenBucketEngine(2, 1.0, clock);

        assertThat(engine.tryConsume().allowed()).isTrue();
        assertThat(engine.tryConsume().allowed()).isTrue();
        assertThat(engine.tryConsume().allowed()).isFalse();

        clock.advanceSeconds(1);
        RateLimitResult afterRefill = engine.tryConsume();
        assertThat(afterRefill.allowed()).isTrue();
        assertThat(afterRefill.remaining()).isEqualTo(0);

        assertThat(engine.tryConsume().allowed()).isFalse();

        clock.advanceSeconds(1);
        assertThat(engine.tryConsume().allowed()).isTrue();
    }

    @Test
    void remainingFloorsFractionalTokens() {
        MutableClock clock = new MutableClock(T0);
        TokenBucketEngine engine = new TokenBucketEngine(1, 0.5, clock);

        assertThat(engine.tryConsume().allowed()).isTrue();
        assertThat(engine.tryConsume().allowed()).isFalse();

        clock.advanceMillis(1500); // 0.75 tokens refilled
        RateLimitResult mid = engine.tryConsume();
        assertThat(mid.allowed()).isFalse();
        assertThat(mid.remaining()).isEqualTo(0);

        clock.advanceMillis(500); // total 2.0s → 1.0 token
        RateLimitResult allow = engine.tryConsume();
        assertThat(allow.allowed()).isTrue();
        assertThat(allow.remaining()).isEqualTo(0);
    }

    @Test
    void resetAtIsWhenBucketWouldBeFull() {
        MutableClock clock = new MutableClock(T0);
        TokenBucketEngine engine = new TokenBucketEngine(10, 2.0, clock);

        engine.tryConsume(); // 9 tokens left → 0.5s to full
        RateLimitResult result = engine.tryConsume(); // 8 tokens → 1.0s to full
        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(8);
        assertThat(result.resetAt()).isEqualTo(T0.plusSeconds(1));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        void advanceMillis(long millis) {
            instant = instant.plusMillis(millis);
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
