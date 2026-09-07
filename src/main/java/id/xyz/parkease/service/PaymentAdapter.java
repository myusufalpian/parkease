package id.xyz.parkease.service;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentAdapter {

    PaymentResult capture(UUID reservationId, BigDecimal amount, String currency, String idempotencyKey);

    PaymentResult refund(String paymentTransactionId, BigDecimal amount, String currency, String idempotencyKey);

    record PaymentResult(boolean successful, String providerReference, String failureReason) {
    }
}
