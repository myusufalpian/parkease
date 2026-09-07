package id.xyz.parkease.exception;

import java.time.OffsetDateTime;

public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String code,
        String message) {
}
