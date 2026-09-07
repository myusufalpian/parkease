package id.xyz.parkease.exception;

import org.springframework.http.HttpStatus;

public class BusinessValidationException extends ApiException {

    private static final HttpStatus UNPROCESSABLE_CONTENT = HttpStatus.valueOf(422);

    public BusinessValidationException(String message) {
        super(UNPROCESSABLE_CONTENT, "BUSINESS_VALIDATION_FAILED", message);
    }
}
