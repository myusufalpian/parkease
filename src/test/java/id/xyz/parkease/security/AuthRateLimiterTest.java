package id.xyz.parkease.security;

import id.xyz.parkease.exception.TooManyRequestsException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthRateLimiterTest {

    @Test
    void rejectsRequestsAfterTenAttemptsPerOperationAndIp() {
        AuthRateLimiter limiter = new AuthRateLimiter(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

        for (int attempt = 0; attempt < 10; attempt++) {
            assertDoesNotThrow(() -> limiter.check("login", "203.0.113.10"));
        }

        assertThrows(TooManyRequestsException.class, () -> limiter.check("login", "203.0.113.10"));
        assertDoesNotThrow(() -> limiter.check("register", "203.0.113.10"));
        assertDoesNotThrow(() -> limiter.check("login", "203.0.113.11"));
    }

    @Test
    void resetsWindowAtExactlyOneMinute() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        AuthRateLimiter limiter = new AuthRateLimiter(clock);

        for (int attempt = 0; attempt < 10; attempt++) {
            assertDoesNotThrow(() -> limiter.check("login", "203.0.113.10"));
        }

        assertThrows(TooManyRequestsException.class, () -> limiter.check("login", "203.0.113.10"));
        clock.set(Instant.parse("2026-01-01T00:01:00Z"));
        assertDoesNotThrow(() -> limiter.check("login", "203.0.113.10"));
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        private MutableClock(Instant initial) {
            this.current = new AtomicReference<>(initial);
        }

        private void set(Instant instant) {
            current.set(instant);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}
