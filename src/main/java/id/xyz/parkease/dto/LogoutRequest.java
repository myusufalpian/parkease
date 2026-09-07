package id.xyz.parkease.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LogoutRequest(@NotBlank @Size(max = 256) String refreshToken) {
}
