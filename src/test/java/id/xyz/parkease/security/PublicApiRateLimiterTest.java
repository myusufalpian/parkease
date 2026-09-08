package id.xyz.parkease.security;

import id.xyz.parkease.exception.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicApiRateLimiterTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2024-01-15T01:00:00Z"), ZoneOffset.UTC);
    private static final String CLIENT = "127.0.0.1";
    private static final String OPERATION = "lots:list";

    @Test
    void allowsUpToSixtyRequests() {
        PublicApiRateLimiter limiter = new PublicApiRateLimiter(FIXED);
        for (int index = 0; index < 60; index++) {
            limiter.check(OPERATION, CLIENT);
        }
        assertDoesNotThrow(() -> limiter.check(OPERATION, CLIENT + "-other"));
    }

    @Test
    void rejectsSixtyFirstRequest() {
        PublicApiRateLimiter limiter = new PublicApiRateLimiter(FIXED);
        for (int index = 0; index < 60; index++) {
            limiter.check(OPERATION, CLIENT);
        }
        assertThrows(TooManyRequestsException.class, () -> limiter.check(OPERATION, CLIENT));
    }

    @Test
    void resetsAfterWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2024-01-15T01:00:00Z"), ZoneOffset.UTC);
        PublicApiRateLimiter limiter = new PublicApiRateLimiter(clock);
        for (int index = 0; index < 60; index++) {
            limiter.check(OPERATION, CLIENT);
        }
        assertThrows(TooManyRequestsException.class, () -> limiter.check(OPERATION, CLIENT));
        clock.advance(Duration.ofMinutes(2));
        assertDoesNotThrow(() -> limiter.check(OPERATION, CLIENT));
    }

    private static class MutableClock extends Clock {
        private Instant instant;
        private final ZoneOffset zone;

        MutableClock(Instant instant, ZoneOffset zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override public ZoneOffset getZone() { return zone; }

        @Override public Clock withZone(java.time.ZoneId zone) { return this; }

        @Override public Instant instant() { return instant; }
    }

    @Test
    void differentClientsHaveSeparateBuckets() {
        PublicApiRateLimiter limiter = new PublicApiRateLimiter(FIXED);
        for (int index = 0; index < 60; index++) {
            limiter.check(OPERATION, "10.0.0.1");
        }
        assertDoesNotThrow(() -> limiter.check(OPERATION, "10.0.0.2"));
        assertThrows(TooManyRequestsException.class, () -> limiter.check(OPERATION, "10.0.0.1"));
    }
}
