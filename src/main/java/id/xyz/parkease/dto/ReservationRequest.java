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
        @NotNull OffsetDateTime plannedEnd) {

    @AssertTrue(message = "planned end must be after planned start")
    public boolean isPlannedWindowValid() {
        return plannedStart == null || plannedEnd == null || plannedStart.isBefore(plannedEnd);
    }
}
