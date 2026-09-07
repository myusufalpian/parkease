package id.xyz.parkease.dto;

import id.xyz.parkease.domain.Reservation.Status;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        Status status,
        SlotResponse slot,
        String plate,
        OffsetDateTime plannedStart,
        OffsetDateTime plannedEnd,
        OffsetDateTime actualStart,
        OffsetDateTime actualEnd,
        String cancellationReason,
        boolean lateCancellation) {

    public record SlotResponse(UUID id, String slotId, String vehicleType, Integer floor) {
    }
}
