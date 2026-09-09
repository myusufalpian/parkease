package id.xyz.parkease.dto;

import java.util.UUID;

public record LotResponse(UUID id, String name, String location, String timezone) {
}
