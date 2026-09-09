package id.xyz.parkease.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record CreateBlockRequest(
        @NotNull OffsetDateTime blockedStart,
        @NotNull OffsetDateTime blockedEnd,
        @NotNull @Size(max = 255) String reason) {
}
