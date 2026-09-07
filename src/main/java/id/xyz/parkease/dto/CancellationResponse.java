package id.xyz.parkease.dto;

import java.math.BigDecimal;

public record CancellationResponse(
        ReservationResponse reservation, String refundStatus, BigDecimal cancellationFee, BigDecimal refundAmount) {
}
