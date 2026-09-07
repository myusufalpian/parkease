package id.xyz.parkease.service;

import id.xyz.parkease.config.BillingProperties;
import id.xyz.parkease.service.CancellationFeeCalculator.CancellationFeeBreakdown;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CancellationFeeCalculatorTest {

    private final CancellationFeeCalculator calculator =
            new CancellationFeeCalculator(new BillingProperties(10, new BigDecimal("0.10")));

    @Test
    void retainsTenPercentAndRefundsNinetyPercent() {
        CancellationFeeBreakdown breakdown = calculator.calculate(new BigDecimal("50000.00"));

        assertEquals(new BigDecimal("5000.00"), breakdown.cancellationFee());
        assertEquals(new BigDecimal("45000.00"), breakdown.refundAmount());
    }

    @Test
    void feePlusRefundEqualsTotalUnderRoundingBoundary() {
        BigDecimal totalPaid = new BigDecimal("12345.67");

        CancellationFeeBreakdown breakdown = calculator.calculate(totalPaid);

        assertEquals(new BigDecimal("1234.57"), breakdown.cancellationFee());
        assertEquals(new BigDecimal("11111.10"), breakdown.refundAmount());
        assertEquals(totalPaid, breakdown.cancellationFee().add(breakdown.refundAmount()));
    }

    @Test
    void zeroTotalProducesZeroFeeAndZeroRefund() {
        CancellationFeeBreakdown breakdown = calculator.calculate(new BigDecimal("0.00"));

        assertEquals(new BigDecimal("0.00"), breakdown.cancellationFee());
        assertEquals(new BigDecimal("0.00"), breakdown.refundAmount());
    }
}
