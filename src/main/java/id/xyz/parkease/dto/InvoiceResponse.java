package id.xyz.parkease.dto;

import id.xyz.parkease.domain.ParkingInvoice.PaymentStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        UUID reservationId,
        long durationMinutes,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal total,
        String currency,
        PaymentStatus paymentStatus,
        OffsetDateTime generatedAt,
        OffsetDateTime paidAt) {
}
