package vasconcelos.volleymatch;

import org.junit.jupiter.api.Test;
import vasconcelos.volleymatch.config.PublicRateLimiter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PublicRateLimiterTest {

    private static Clock at(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    @Test
    void should_block_when_limit_exceeded_within_same_minute() {
        PublicRateLimiter limiter = new PublicRateLimiter(at("2026-11-05T19:00:15Z"), 3);

        assertThat(limiter.tryAcquire("1.2.3.4")).isZero();
        assertThat(limiter.tryAcquire("1.2.3.4")).isZero();
        assertThat(limiter.tryAcquire("1.2.3.4")).isZero();
        assertThat(limiter.tryAcquire("1.2.3.4")).isEqualTo(45);
    }

    @Test
    void should_count_each_ip_separately() {
        PublicRateLimiter limiter = new PublicRateLimiter(at("2026-11-05T19:00:15Z"), 1);

        assertThat(limiter.tryAcquire("1.1.1.1")).isZero();
        assertThat(limiter.tryAcquire("2.2.2.2")).isZero();
        assertThat(limiter.tryAcquire("1.1.1.1")).isPositive();
    }
}
