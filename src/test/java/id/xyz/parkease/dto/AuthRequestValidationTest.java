package id.xyz.parkease.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static String repeat(char c, int length) {
        return String.valueOf(c).repeat(length);
    }

    @Test
    void passwordOf72BytesIsAccepted() {
        assertTrue(validator.validate(new RegisterRequest("driver", repeat('a', 72))).isEmpty());
    }

    @Test
    void passwordOver72BytesIsRejected() {
        assertFalse(validator.validate(new RegisterRequest("driver", repeat('a', 73))).isEmpty());
    }

    @Test
    void multibytePasswordExceeding72BytesIsRejected() {
        // 'é' is 2 bytes in UTF-8, so 37 of them = 74 bytes but only 37 chars.
        assertFalse(validator.validate(new RegisterRequest("driver", repeat('é', 37))).isEmpty());
    }

    @Test
    void usernameOverMaxLengthIsRejected() {
        assertFalse(validator.validate(new RegisterRequest(repeat('u', 101), "password1")).isEmpty());
    }

    @Test
    void loginUsernameOverMaxLengthIsRejected() {
        assertFalse(validator.validate(new LoginRequest(repeat('u', 101), "password1")).isEmpty());
    }

    @Test
    void refreshTokenOverMaxLengthIsRejected() {
        assertFalse(validator.validate(new RefreshRequest(repeat('t', 257))).isEmpty());
    }

    @Test
    void wellFormedRequestsHaveNoViolations() {
        assertTrue(validator.validate(new RegisterRequest("driver", "password1")).isEmpty());
        assertTrue(validator.validate(new LoginRequest("driver", "password1")).isEmpty());
        assertTrue(validator.validate(new RefreshRequest(repeat('t', 86))).isEmpty());
    }
}
