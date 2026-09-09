package id.xyz.parkease.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BlockResponse(
        UUID id,
        UUID slotId,
        OffsetDateTime blockedStart,
        OffsetDateTime blockedEnd,
        String reason,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
