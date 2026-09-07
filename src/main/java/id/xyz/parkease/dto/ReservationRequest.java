package id.xyz.parkease.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReservationRequest(
        @NotNull UUID lotId,
        @NotBlank @Size(max = 50) String vehicleType,
        @NotBlank @Size(max = 20) String plate,
        @NotNull OffsetDateTime plannedStart,
        @NotNull OffsetDateTime plannedEnd,
        @Size(max = 50) String promoCode) {

    public ReservationRequest(UUID lotId, String vehicleType, String plate, OffsetDateTime plannedStart, OffsetDateTime plannedEnd) {
        this(lotId, vehicleType, plate, plannedStart, plannedEnd, null);
    }

    @AssertTrue(message = "planned end must be after planned start")
    public boolean isPlannedWindowValid() {
        return plannedStart == null || plannedEnd == null || plannedStart.isBefore(plannedEnd);
    }
}
