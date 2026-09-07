package id.xyz.parkease.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RefreshTokenGeneratorTest {

    private final RefreshTokenGenerator generator = new RefreshTokenGenerator();

    @Test
    void generateRawTokenProducesNonBlankValue() {
        String token = generator.generateRawToken();
        assertNotNull(token);
        assertNotEquals("", token);
    }

    @Test
    void generateRawTokenValuesAreUnique() {
        String first = generator.generateRawToken();
        String second = generator.generateRawToken();
        assertNotEquals(first, second);
    }

    @Test
    void hashIsDeterministicForSameInput() {
        String rawToken = generator.generateRawToken();
        assertEquals(generator.hash(rawToken), generator.hash(rawToken));
    }

    @Test
    void hashDiffersForDifferentInput() {
        String first = generator.generateRawToken();
        String second = generator.generateRawToken();
        assertNotEquals(generator.hash(first), generator.hash(second));
    }

    @Test
    void hashIsNotEqualToRawToken() {
        String rawToken = generator.generateRawToken();
        assertNotEquals(rawToken, generator.hash(rawToken));
    }
}
