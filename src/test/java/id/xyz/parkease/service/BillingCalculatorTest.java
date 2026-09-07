package id.xyz.parkease.service;

import id.xyz.parkease.domain.ParkingLot;
import id.xyz.parkease.domain.RateCard;
import id.xyz.parkease.domain.PricingPromotion;
import id.xyz.parkease.dto.BillingBreakdown;
import id.xyz.parkease.exception.BusinessValidationException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BillingCalculatorTest {

    private static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");
    private static final BigDecimal HOURLY_RATE = new BigDecimal("10000.00");
    private static final BigDecimal DAILY_CAP = new BigDecimal("30000.00");
    private static final BigDecimal OVERNIGHT_SURCHARGE = new BigDecimal("15000.00");

    private final BillingCalculator calculator = new BillingCalculator();

    private RateCard rateCard() {
        ParkingLot lot = ParkingLot.builder().name("Lot").timezone(JAKARTA.getId()).build();
        return RateCard.builder()
                .lot(lot)
                .vehicleType("CAR")
                .version(1)
                .hourlyRate(HOURLY_RATE)
                .dailyCap(DAILY_CAP)
                .overnightSurcharge(OVERNIGHT_SURCHARGE)
                .currency("IDR")
                .effectiveFrom(OffsetDateTime.parse("2024-01-01T00:00:00+07:00"))
                .build();
    }

    @Test
    void exactThirtyMinutesIsOneBlock() {
        BillingBreakdown breakdown = calculator.calculate(
                OffsetDateTime.parse("2024-01-15T09:00:00+07:00"),
                OffsetDateTime.parse("2024-01-15T09:30:00+07:00"),
                JAKARTA, rateCard());

        assertEquals(30L, breakdown.durationMinutes());
        assertEquals(new BigDecimal("5000.00"), breakdown.total());
    }

    @Test
    void thirtyOneMinutesRoundsUpToTwoBlocks() {
        BillingBreakdown breakdown = calculator.calculate(
                OffsetDateTime.parse("2024-01-15T09:00:00+07:00"),
                OffsetDateTime.parse("2024-01-15T09:31:00+07:00"),
                JAKARTA, rateCard());

        assertEquals(31L, breakdown.durationMinutes());
        assertEquals(new BigDecimal("10000.00"), breakdown.total());
    }

    @Test
    void sixtyMinutesIsTwoBlocksSameDayNoSurcharge() {
        BillingBreakdown breakdown = calculator.calculate(
                OffsetDateTime.parse("2024-01-15T09:00:00+07:00"),
                OffsetDateTime.parse("2024-01-15T10:00:00+07:00"),
                JAKARTA, rateCard());

        assertEquals(new BigDecimal("10000.00"), breakdown.subtotal());
        assertEquals(new BigDecimal("10000.00"), breakdown.total());
    }

    @Test
    void dailyCapLimitsSingleDaySubtotal() {
        BillingBreakdown breakdown = calculator.calculate(
                OffsetDateTime.parse("2024-01-15T00:00:00+07:00"),
                OffsetDateTime.parse("2024-01-15T08:00:00+07:00"),
                JAKARTA, rateCard());

        assertEquals(480L, breakdown.durationMinutes());
        assertEquals(new BigDecimal("30000.00"), breakdown.subtotal());
        assertEquals(new BigDecimal("30000.00"), breakdown.total());
    }

    @Test
    void midnightCrossingSpansTwoLocalDatesAndAddsOneSurcharge() {
        BillingBreakdown breakdown = calculator.calculate(
                OffsetDateTime.parse("2024-01-15T23:00:00+07:00"),
                OffsetDateTime.parse("2024-01-16T01:00:00+07:00"),
                JAKARTA, rateCard());

        assertEquals(120L, breakdown.durationMinutes());
        assertEquals(new BigDecimal("20000.00"), breakdown.subtotal());
        assertEquals(new BigDecimal("35000.00"), breakdown.total());
    }

    @Test
    void currencyAndSnapshotComeFromRateCard() {
        BillingBreakdown breakdown = calculator.calculate(
                OffsetDateTime.parse("2024-01-15T09:00:00+07:00"),
                OffsetDateTime.parse("2024-01-15T09:30:00+07:00"),
                JAKARTA, rateCard());

        assertEquals("IDR", breakdown.currency());
        assertEquals(1, breakdown.pricingSnapshot().rateCardVersion());
        assertEquals(HOURLY_RATE, breakdown.pricingSnapshot().hourlyRate());
        assertEquals(new BigDecimal("0.00"), breakdown.discountAmount());
    }

    @Test
    void plannedEndBeforeStartIsRejected() {
        assertThrows(BusinessValidationException.class, () -> calculator.calculate(
                OffsetDateTime.parse("2024-01-15T10:00:00+07:00"),
                OffsetDateTime.parse("2024-01-15T09:00:00+07:00"),
                JAKARTA, rateCard()));
    }

    @Test
    void plannedEndEqualToStartIsRejected() {
        assertThrows(BusinessValidationException.class, () -> calculator.calculate(
                OffsetDateTime.parse("2024-01-15T09:00:00+07:00"),
                OffsetDateTime.parse("2024-01-15T09:00:00+07:00"),
                JAKARTA, rateCard()));
    }

    @Test
    void nullRateCardIsRejected() {
        assertThrows(NullPointerException.class, () -> calculator.calculate(
                OffsetDateTime.parse("2024-01-15T09:00:00+07:00"),
                OffsetDateTime.parse("2024-01-15T09:30:00+07:00"),
                JAKARTA, null));
    }

    @Test
    void percentagePromotionDiscountsSubtotalAndUpdatesSnapshot() {
        BillingBreakdown breakdown = calculator.calculate(
                OffsetDateTime.parse("2024-01-15T09:00:00+07:00"),
                OffsetDateTime.parse("2024-01-15T10:00:00+07:00"), JAKARTA, rateCard());
        PricingPromotion promotion = PricingPromotion.builder()
                .code("PROMO10")
                .effectiveFrom(OffsetDateTime.parse("2024-01-01T00:00:00+07:00"))
                .effectiveTo(OffsetDateTime.parse("2024-02-01T00:00:00+07:00"))
                .discountType(PricingPromotion.DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10"))
                .build();

        BillingBreakdown discounted = calculator.applyPromotion(breakdown, promotion);

        assertEquals(new BigDecimal("1000.00"), discounted.discountAmount());
        assertEquals(new BigDecimal("9000.00"), discounted.total());
        assertEquals("PROMO10", discounted.pricingSnapshot().promoCode());
    }

    @Test
    void fixedPromotionCannotReduceTotalBelowZero() {
        BillingBreakdown breakdown = calculator.calculate(
                OffsetDateTime.parse("2024-01-15T09:00:00+07:00"),
                OffsetDateTime.parse("2024-01-15T09:30:00+07:00"), JAKARTA, rateCard());
        PricingPromotion promotion = PricingPromotion.builder()
                .code("FREE")
                .effectiveFrom(OffsetDateTime.parse("2024-01-01T00:00:00+07:00"))
                .effectiveTo(OffsetDateTime.parse("2024-02-01T00:00:00+07:00"))
                .discountType(PricingPromotion.DiscountType.FIXED)
                .discountValue(new BigDecimal("999999"))
                .build();

        BillingBreakdown discounted = calculator.applyPromotion(breakdown, promotion);

        assertEquals(new BigDecimal("5000.00"), discounted.discountAmount());
        assertEquals(new BigDecimal("0.00"), discounted.total());
    }
}
