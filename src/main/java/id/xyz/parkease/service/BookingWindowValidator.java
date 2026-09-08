package id.xyz.parkease.service;

import id.xyz.parkease.config.BookingProperties;
import id.xyz.parkease.exception.BusinessValidationException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class BookingWindowValidator {

    private static final long MAX_RESERVATION_DAYS = 31L;

    private final BookingProperties bookingProperties;
    private final Clock clock;

    public BookingWindowValidator(BookingProperties bookingProperties, Clock clock) {
        this.bookingProperties = Objects.requireNonNull(bookingProperties);
        this.clock = Objects.requireNonNull(clock);
    }

    public void validate(OffsetDateTime plannedStart, OffsetDateTime plannedEnd) {
        validate(plannedStart, plannedEnd, currentTime());
    }

    public void validate(OffsetDateTime plannedStart, OffsetDateTime plannedEnd, OffsetDateTime now) {
        if (plannedStart == null || plannedEnd == null || !plannedStart.isBefore(plannedEnd)) {
            throw new BusinessValidationException("planned end must be after planned start");
        }
        if (plannedStart.plusDays(MAX_RESERVATION_DAYS).isBefore(plannedEnd)) {
            throw new BusinessValidationException("planned window exceeds the maximum allowed duration");
        }
        long minutes = java.time.Duration.between(plannedStart, plannedEnd).toMinutes();
        if (minutes < bookingProperties.minimumDurationMinutes()) {
            throw new BusinessValidationException("planned duration is below the minimum allowed duration");
        }
        if (plannedStart.isBefore(now)) {
            throw new BusinessValidationException("planned start must not be in the past");
        }
        OffsetDateTime horizon = now.plusDays(bookingProperties.futureHorizonDays());
        if (plannedStart.isAfter(horizon)) {
            throw new BusinessValidationException("planned start exceeds the future booking horizon");
        }
    }

    private OffsetDateTime currentTime() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
