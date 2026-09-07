package id.xyz.parkease.dto;

import jakarta.validation.constraints.Size;

public record CancelReservationRequest(@Size(max = 100) String reason) {
}
