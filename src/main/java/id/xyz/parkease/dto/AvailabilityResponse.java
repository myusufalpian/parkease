package id.xyz.parkease.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AvailabilityResponse(
        UUID lotId,
        OffsetDateTime plannedStart,
        OffsetDateTime plannedEnd,
        List<AvailableSlotResponse> slots) {

    public AvailabilityResponse {
        slots = List.copyOf(slots);
    }
}
