package id.xyz.parkease.dto;

import java.math.BigDecimal;

public record ExtensionChargeResponse(ReservationResponse reservation, long additionalDurationMinutes, BigDecimal amount, String currency) {
}
