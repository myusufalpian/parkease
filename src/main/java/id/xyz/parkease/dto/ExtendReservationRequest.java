package id.xyz.parkease.dto;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record ExtendReservationRequest(@NotNull OffsetDateTime plannedEnd) {
}
