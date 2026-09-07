package id.xyz.parkease.dto;

import java.math.BigDecimal;

public record BillingBreakdown(
        long durationMinutes,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal total,
        String currency,
        PricingSnapshot pricingSnapshot) {
}
