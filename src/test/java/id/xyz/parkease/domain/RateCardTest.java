package id.xyz.parkease.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateCardTest {

    private static final OffsetDateTime EFFECTIVE_FROM = OffsetDateTime.parse("2024-01-15T00:00:00+07:00");
    private static final OffsetDateTime EFFECTIVE_TO = OffsetDateTime.parse("2024-02-15T00:00:00+07:00");

    private RateCard rateCard(OffsetDateTime effectiveFrom, OffsetDateTime effectiveTo) {
        return RateCard.builder()
                .lot(ParkingLot.builder().name("Lot").timezone("Asia/Jakarta").build())
                .vehicleType("CAR")
                .hourlyRate(new BigDecimal("10000.00"))
                .dailyCap(new BigDecimal("30000.00"))
                .overnightSurcharge(new BigDecimal("15000.00"))
                .effectiveFrom(effectiveFrom)
                .effectiveTo(effectiveTo)
                .build();
    }

    @Test
    void effectiveAtExactStartIsInclusive() {
        assertTrue(rateCard(EFFECTIVE_FROM, EFFECTIVE_TO).isEffectiveAt(EFFECTIVE_FROM));
    }

    @Test
    void notEffectiveBeforeStart() {
        assertFalse(rateCard(EFFECTIVE_FROM, EFFECTIVE_TO).isEffectiveAt(EFFECTIVE_FROM.minusSeconds(1)));
    }

    @Test
    void effectiveWithinInterval() {
        assertTrue(rateCard(EFFECTIVE_FROM, EFFECTIVE_TO).isEffectiveAt(OffsetDateTime.parse("2024-01-20T12:00:00+07:00")));
    }

    @Test
    void notEffectiveAtExactEndIsExclusive() {
        assertFalse(rateCard(EFFECTIVE_FROM, EFFECTIVE_TO).isEffectiveAt(EFFECTIVE_TO));
    }

    @Test
    void nullEndIsOpenEnded() {
        assertTrue(rateCard(EFFECTIVE_FROM, null).isEffectiveAt(OffsetDateTime.parse("2030-01-01T00:00:00+07:00")));
    }
}
