package id.xyz.parkease.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReservationCheckedOutEvent(UUID reservationId, OffsetDateTime actualEnd) {
}
