package id.xyz.parkease.dto;

import java.util.UUID;

public record AvailableSlotResponse(UUID id, String slotId, String vehicleType, Integer floor) {
}
