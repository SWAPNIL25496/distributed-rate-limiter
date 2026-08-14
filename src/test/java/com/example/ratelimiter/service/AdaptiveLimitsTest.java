package com.example.ratelimiter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AdaptiveLimitsTest {

    @ParameterizedTest
    @CsvSource({
        "0.0, 1.0",
        "0.19, 1.0",
        "0.2, 0.5",
        "0.49, 0.5",
        "0.5, 0.25",
        "1.0, 0.25"
    })
    void mapsErrorRateToMultiplier(double errorRate, double expected) {
        assertThat(AdaptiveLimits.multiplierFor(errorRate)).isEqualTo(expected);
    }

    @Test
    void rejectsOutOfRangeErrorRate() {
        assertThatThrownBy(() -> AdaptiveLimits.multiplierFor(-0.01))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AdaptiveLimits.multiplierFor(1.01))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void effectiveLimitFloorsAndEnforcesMinimumOne() {
        assertThat(AdaptiveLimits.effectiveLimit(100, 0.5)).isEqualTo(50);
        assertThat(AdaptiveLimits.effectiveLimit(100, 0.25)).isEqualTo(25);
        assertThat(AdaptiveLimits.effectiveLimit(3, 0.25)).isEqualTo(1);
        assertThat(AdaptiveLimits.effectiveLimit(1, 0.25)).isEqualTo(1);
    }
}
