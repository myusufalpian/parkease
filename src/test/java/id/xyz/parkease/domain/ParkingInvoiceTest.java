package id.xyz.parkease.domain;

import id.xyz.parkease.domain.ParkingInvoice.PaymentStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ParkingInvoiceTest {

    private static final OffsetDateTime PAID_AT = OffsetDateTime.parse("2024-01-15T09:05:00Z");

    private ParkingInvoice pendingInvoice() {
        return ParkingInvoice.builder()
                .durationMinutes(60)
                .subtotal(new BigDecimal("10000.00"))
                .total(new BigDecimal("10000.00"))
                .currency("IDR")
                .build();
    }

    @Test
    void newInvoiceDefaultsToPaymentPending() {
        assertEquals(PaymentStatus.PAYMENT_PENDING, pendingInvoice().getPaymentStatus());
    }

    @Test
    void markPaidTransitionsAndRecordsPaidAt() {
        ParkingInvoice paid = pendingInvoice().markPaid(PAID_AT);

        assertEquals(PaymentStatus.PAID, paid.getPaymentStatus());
        assertEquals(PAID_AT, paid.getPaidAt());
    }

    @Test
    void markFailedTransitionsWithoutPaidAt() {
        ParkingInvoice failed = pendingInvoice().markFailed();

        assertEquals(PaymentStatus.FAILED, failed.getPaymentStatus());
        assertNull(failed.getPaidAt());
    }

    @Test
    void markExpiredTransitionsWithoutPaidAt() {
        ParkingInvoice expired = pendingInvoice().markExpired();

        assertEquals(PaymentStatus.EXPIRED, expired.getPaymentStatus());
        assertNull(expired.getPaidAt());
    }

    @Test
    void applyDefaultsFillsNulls() {
        ParkingInvoice invoice = new ParkingInvoice(
                null, null, 60, new BigDecimal("10000.00"), null, new BigDecimal("10000.00"), "IDR", null, null, null, null);
        invoice.applyDefaults();

        assertNotNull(invoice.getId());
        assertEquals(BigDecimal.ZERO, invoice.getDiscountAmount());
        assertEquals(PaymentStatus.PAYMENT_PENDING, invoice.getPaymentStatus());
        assertNotNull(invoice.getGeneratedAt());
    }
}
