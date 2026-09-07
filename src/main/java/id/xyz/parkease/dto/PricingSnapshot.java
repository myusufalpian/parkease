package id.xyz.parkease.dto;

import java.math.BigDecimal;

public record PricingSnapshot(
        int rateCardVersion,
        String demandMetric,
        String promoCode,
        BigDecimal hourlyRate,
        BigDecimal dailyCap,
        int graceMinutes,
        BigDecimal overnightSurcharge) {
}
