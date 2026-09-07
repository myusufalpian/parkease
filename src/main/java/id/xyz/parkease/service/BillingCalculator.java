package id.xyz.parkease.service;

import id.xyz.parkease.domain.RateCard;
import id.xyz.parkease.domain.PricingPromotion;
import id.xyz.parkease.dto.BillingBreakdown;
import id.xyz.parkease.dto.PricingSnapshot;
import id.xyz.parkease.exception.BusinessValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

@Component
public class BillingCalculator {

    private static final long BLOCK_SECONDS = 30L * 60L;
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal HALF = new BigDecimal("0.5");

    public BillingBreakdown calculate(
            OffsetDateTime plannedStart, OffsetDateTime plannedEnd, ZoneId lotTimezone, RateCard rateCard) {
        return calculate(plannedStart, plannedEnd, lotTimezone, rateCard, null);
    }

    public BillingBreakdown calculate(
            OffsetDateTime plannedStart, OffsetDateTime plannedEnd, ZoneId lotTimezone, RateCard rateCard, String demandMetric) {
        Objects.requireNonNull(plannedStart, "plannedStart must not be null");
        Objects.requireNonNull(plannedEnd, "plannedEnd must not be null");
        Objects.requireNonNull(lotTimezone, "lotTimezone must not be null");
        Objects.requireNonNull(rateCard, "rateCard must not be null");
        if (!plannedStart.isBefore(plannedEnd)) {
            throw new BusinessValidationException("planned end must be after planned start");
        }

        Duration duration = Duration.between(plannedStart, plannedEnd);
        long durationMinutes = duration.toMinutes();
        long blockCount = ceilDiv(duration.getSeconds());
        BigDecimal blockCharge = money(rateCard.getHourlyRate().multiply(HALF));
        BigDecimal dailyCap = money(rateCard.getDailyCap());

        Map<LocalDate, BigDecimal> subtotalByLocalDate = allocateBlocksToLocalDates(
                plannedStart, blockCount, blockCharge, lotTimezone);
        Map<LocalDate, BigDecimal> cappedByLocalDate = applyDailyCap(subtotalByLocalDate, dailyCap);

        BigDecimal subtotal = money(sum(cappedByLocalDate.values()));
        long midnightCrossings = localMidnightCrossings(plannedStart, plannedEnd, lotTimezone);
        BigDecimal surchargeTotal = money(
                rateCard.getOvernightSurcharge().multiply(BigDecimal.valueOf(midnightCrossings)));

        BigDecimal total = money(subtotal.add(surchargeTotal));
        BigDecimal discountAmount = money(BigDecimal.ZERO);

        PricingSnapshot snapshot = new PricingSnapshot(
                rateCard.getVersion(),
                demandMetric,
                null,
                rateCard.getHourlyRate(),
                rateCard.getDailyCap(),
                (int) ReservationService.CHECK_IN_GRACE_MINUTES,
                rateCard.getOvernightSurcharge());

        return new BillingBreakdown(durationMinutes, subtotal, discountAmount, total, rateCard.getCurrency(), snapshot);
    }

    public BillingBreakdown applyPromotion(BillingBreakdown breakdown, PricingPromotion promotion) {
        if (promotion == null) return breakdown;
        BigDecimal discount = promotion.getDiscountType() == PricingPromotion.DiscountType.PERCENTAGE
                ? breakdown.subtotal().multiply(promotion.getDiscountValue()).divide(new BigDecimal("100"), MONEY_SCALE, RoundingMode.HALF_UP)
                : promotion.getDiscountValue();
        discount = discount.max(BigDecimal.ZERO).min(breakdown.subtotal()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        PricingSnapshot snapshot = new PricingSnapshot(breakdown.pricingSnapshot().rateCardVersion(), null,
                promotion.getCode(), breakdown.pricingSnapshot().hourlyRate(), breakdown.pricingSnapshot().dailyCap(),
                breakdown.pricingSnapshot().graceMinutes(), breakdown.pricingSnapshot().overnightSurcharge());
        return new BillingBreakdown(breakdown.durationMinutes(), breakdown.subtotal(), discount,
                breakdown.total().subtract(discount).setScale(MONEY_SCALE, RoundingMode.HALF_UP), breakdown.currency(), snapshot);
    }

    private Map<LocalDate, BigDecimal> allocateBlocksToLocalDates(
            OffsetDateTime plannedStart, long blockCount, BigDecimal blockCharge, ZoneId lotTimezone) {
        Map<LocalDate, BigDecimal> subtotalByLocalDate = new HashMap<>();
        for (long blockIndex = 0; blockIndex < blockCount; blockIndex++) {
            OffsetDateTime blockStart = plannedStart.plusSeconds(blockIndex * BLOCK_SECONDS);
            LocalDate localDate = blockStart.atZoneSameInstant(lotTimezone).toLocalDate();
            subtotalByLocalDate.merge(localDate, blockCharge, BigDecimal::add);
        }
        return subtotalByLocalDate;
    }

    private Map<LocalDate, BigDecimal> applyDailyCap(Map<LocalDate, BigDecimal> subtotalByLocalDate, BigDecimal dailyCap) {
        Map<LocalDate, BigDecimal> capped = new TreeMap<>();
        for (Map.Entry<LocalDate, BigDecimal> entry : subtotalByLocalDate.entrySet()) {
            capped.put(entry.getKey(), money(entry.getValue()).min(dailyCap));
        }
        return capped;
    }

    private long localMidnightCrossings(OffsetDateTime plannedStart, OffsetDateTime plannedEnd, ZoneId lotTimezone) {
        LocalDateTime localStart = plannedStart.atZoneSameInstant(lotTimezone).toLocalDateTime();
        LocalDateTime localEnd = plannedEnd.atZoneSameInstant(lotTimezone).toLocalDateTime();
        LocalDate startDate = localStart.toLocalDate();
        LocalDate lastCoveredDate = localEnd.toLocalTime().equals(LocalTime.MIDNIGHT)
                ? localEnd.toLocalDate().minusDays(1)
                : localEnd.toLocalDate();
        return Math.max(0, ChronoUnit.DAYS.between(startDate, lastCoveredDate));
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(Iterable<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            total = total.add(value);
        }
        return total;
    }

    private long ceilDiv(long numeratorSeconds) {
        return (numeratorSeconds + BLOCK_SECONDS - 1) / BLOCK_SECONDS;
    }
}
