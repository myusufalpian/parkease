package id.xyz.parkease.service;

import id.xyz.parkease.config.BookingProperties;
import id.xyz.parkease.exception.BusinessValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingWindowValidatorTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2024-01-15T08:00:00Z");
    private static final Clock FIXED = Clock.fixed(Instant.parse("2024-01-15T08:00:00Z"), ZoneOffset.UTC);

    private BookingWindowValidator validator;

    @BeforeEach
    void setUp() {
        validator = new BookingWindowValidator(new BookingProperties(30, 90), FIXED);
    }

    @Test
    void acceptsValidWindow() {
        OffsetDateTime start = NOW.plusHours(1);
        OffsetDateTime end = start.plusHours(1);
        assertDoesNotThrow(() -> validator.validate(start, end, NOW));
    }

    @Test
    void rejectsPastStart() {
        OffsetDateTime start = NOW.minusMinutes(1);
        OffsetDateTime end = NOW.plusHours(1);
        assertThrows(BusinessValidationException.class, () -> validator.validate(start, end, NOW));
    }

    @Test
    void rejectsDurationBelowMinimum() {
        OffsetDateTime start = NOW.plusHours(1);
        OffsetDateTime end = start.plusMinutes(29);
        assertThrows(BusinessValidationException.class, () -> validator.validate(start, end, NOW));
    }

    @Test
    void acceptsExactlyMinimumDuration() {
        OffsetDateTime start = NOW.plusHours(1);
        OffsetDateTime end = start.plusMinutes(30);
        assertDoesNotThrow(() -> validator.validate(start, end, NOW));
    }

    @Test
    void rejectsInvalidInterval() {
        OffsetDateTime start = NOW.plusHours(2);
        OffsetDateTime end = NOW.plusHours(1);
        assertThrows(BusinessValidationException.class, () -> validator.validate(start, end, NOW));
    }

    @Test
    void rejectsExceedsMaximumDuration() {
        OffsetDateTime start = NOW.plusHours(1);
        OffsetDateTime end = start.plusDays(32);
        assertThrows(BusinessValidationException.class, () -> validator.validate(start, end, NOW));
    }

    @Test
    void rejectsBeyondFutureHorizon() {
        OffsetDateTime start = NOW.plusDays(91);
        OffsetDateTime end = start.plusHours(1);
        assertThrows(BusinessValidationException.class, () -> validator.validate(start, end, NOW));
    }

    @Test
    void acceptsAtHorizonBoundary() {
        OffsetDateTime start = NOW.plusDays(90);
        OffsetDateTime end = start.plusMinutes(30);
        assertDoesNotThrow(() -> validator.validate(start, end, NOW));
    }
}
