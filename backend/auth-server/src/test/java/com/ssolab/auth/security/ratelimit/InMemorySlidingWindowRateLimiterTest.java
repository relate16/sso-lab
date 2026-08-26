package com.ssolab.auth.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class InMemorySlidingWindowRateLimiterTest {
    @Test
    void rejectsLimitAndAllowsAgainAfterSlidingWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-24T00:00:00Z"));
        InMemorySlidingWindowRateLimiter limiter = new InMemorySlidingWindowRateLimiter(clock);

        assertThat(limiter.tryAcquire("fingerprinted-key", 2, Duration.ofMinutes(10))).isTrue();
        assertThat(limiter.tryAcquire("fingerprinted-key", 2, Duration.ofMinutes(10))).isTrue();
        assertThat(limiter.tryAcquire("fingerprinted-key", 2, Duration.ofMinutes(10))).isFalse();

        clock.advance(Duration.ofMinutes(10).plusMillis(1));
        assertThat(limiter.tryAcquire("fingerprinted-key", 2, Duration.ofMinutes(10))).isTrue();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
