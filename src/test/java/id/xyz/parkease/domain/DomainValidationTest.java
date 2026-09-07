package id.xyz.parkease.domain;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;

class DomainValidationTest {

    private static final String LOT_NAME = "Test Lot";
    private static final String TIMEZONE = "Asia/Jakarta";

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void blankLotNameFailsValidation() {
        ParkingLot lot = ParkingLot.builder().name("").timezone(TIMEZONE).build();
        assertFalse(validator.validate(lot).isEmpty());
    }

    @Test
    void validLotPassesValidation() {
        ParkingLot lot = ParkingLot.builder().name(LOT_NAME).timezone(TIMEZONE).build();
        assertTrue(validator.validate(lot).isEmpty());
    }

    @Test
    void oversizePlateFailsValidation() {
        ParkingSlot slot = ParkingSlot.builder().slotId("A-01").vehicleType("CAR").floor(1).build();
        Reservation reservation = Reservation.builder()
                .slot(slot)
                .customerPlate("PLATE-WAY-TOO-LONG-OVER-20")
                .plannedStart(java.time.OffsetDateTime.now())
                .plannedEnd(java.time.OffsetDateTime.now().plusHours(1))
                .build();
        assertFalse(validator.validate(reservation).isEmpty());
    }

    @Test
    void plannedEndEqualToPlannedStartFailsValidation() {
        OffsetDateTime plannedStart = OffsetDateTime.parse("2024-01-15T09:00:00+07:00");
        Reservation reservation = Reservation.builder()
                .slot(ParkingSlot.builder().slotId("A-01").vehicleType("CAR").floor(1).build())
                .plannedStart(plannedStart)
                .plannedEnd(plannedStart)
                .build();

        assertFalse(validator.validate(reservation).isEmpty());
    }

    @Test
    void demandPricingRuleRejectsInvalidWindowAndMultiplier() {
        DemandPricingRule rule = DemandPricingRule.builder()
                .lot(ParkingLot.builder().name(LOT_NAME).timezone(TIMEZONE).build())
                .effectiveFrom(OffsetDateTime.parse("2024-02-01T00:00:00+07:00"))
                .effectiveTo(OffsetDateTime.parse("2024-01-01T00:00:00+07:00"))
                .occupancyThreshold(new BigDecimal("1.2"))
                .multiplier(BigDecimal.ZERO)
                .build();

        assertFalse(validator.validate(rule).isEmpty());
    }
}
