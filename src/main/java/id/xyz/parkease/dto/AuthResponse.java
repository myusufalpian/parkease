package id.xyz.parkease.dto;

public record AuthResponse(String accessToken, String refreshToken, long accessTokenExpiresInSeconds) {
}
