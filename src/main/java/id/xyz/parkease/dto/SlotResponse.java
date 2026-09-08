package id.xyz.parkease.dto;

import java.util.UUID;

public record SlotResponse(UUID id, String slotId, String vehicleType, int floor, String status) {
}
