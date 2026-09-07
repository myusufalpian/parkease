package id.xyz.parkease.service;

import id.xyz.parkease.config.BillingProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class CancellationFeeCalculator {

    private static final int MONEY_SCALE = 2;

    private final BillingProperties billingProperties;

    public CancellationFeeCalculator(BillingProperties billingProperties) {
        this.billingProperties = Objects.requireNonNull(billingProperties);
    }

    public CancellationFeeBreakdown calculate(BigDecimal totalPaid) {
        Objects.requireNonNull(totalPaid, "totalPaid must not be null");
        BigDecimal fee = totalPaid.multiply(billingProperties.cancellationFeeRate())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal refund = totalPaid.subtract(fee).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new CancellationFeeBreakdown(fee, refund);
    }

    public record CancellationFeeBreakdown(BigDecimal cancellationFee, BigDecimal refundAmount) {
    }
}
