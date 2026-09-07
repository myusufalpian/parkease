package id.xyz.parkease.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 100) String username,
        @NotBlank @Size(min = 8, max = 72) String password) {

    // BCrypt only hashes the first 72 bytes; reject anything longer so the whole
    // password is actually part of the stored secret (multibyte-safe byte check).
    @AssertTrue(message = "password must not exceed 72 bytes")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
