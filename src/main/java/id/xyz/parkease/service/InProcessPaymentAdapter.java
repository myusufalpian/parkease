package id.xyz.parkease.service;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!prod")
public class InProcessPaymentAdapter implements PaymentAdapter {

    private static final String REFERENCE_PREFIX = "IN-PROCESS-";

    @Override
    public PaymentResult capture(UUID reservationId, BigDecimal amount, String currency, String idempotencyKey) {
        return new PaymentResult(true, REFERENCE_PREFIX + idempotencyKey, null);
    }

    @Override
    public PaymentResult refund(String paymentTransactionId, BigDecimal amount, String currency, String idempotencyKey) {
        return new PaymentResult(true, REFERENCE_PREFIX + idempotencyKey, null);
    }
}
