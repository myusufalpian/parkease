package id.xyz.parkease.service;

import id.xyz.parkease.domain.DemandPricingRule;
import id.xyz.parkease.domain.ParkingLot;
import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.domain.RateCard;
import id.xyz.parkease.domain.Reservation;
import id.xyz.parkease.repository.DemandPricingRuleRepository;
import id.xyz.parkease.repository.ParkingLotRepository;
import id.xyz.parkease.repository.ParkingSlotRepository;
import id.xyz.parkease.repository.ReservationRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DemandPricingService.class)
class DemandPricingServiceTest {
    private static final OffsetDateTime START = OffsetDateTime.parse("2024-01-15T09:00:00+07:00");

    @Autowired private DemandPricingService demandPricingService;
    @Autowired private DemandPricingRuleRepository ruleRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ParkingSlotRepository slotRepository;
    @Autowired private ParkingLotRepository lotRepository;

    @BeforeEach
    void clean() {
        reservationRepository.deleteAll();
        ruleRepository.deleteAll();
        slotRepository.deleteAll();
        lotRepository.deleteAll();
    }

    @Test
    void appliesMatchingMultiplierButExcludesCurrentReservation() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("Lot").timezone("Asia/Jakarta").build());
        ParkingSlot occupied = slotRepository.saveAndFlush(slot(lot, "A-01"));
        slotRepository.saveAndFlush(slot(lot, "A-02"));
        Reservation existing = reservationRepository.saveAndFlush(Reservation.builder().slot(occupied)
                .customerPlate("B1234").plannedStart(START).plannedEnd(START.plusHours(1)).status(Reservation.Status.ACTIVE).build());
        DemandPricingRule rule = ruleRepository.saveAndFlush(DemandPricingRule.builder().lot(lot).vehicleType("CAR")
                .effectiveFrom(START.minusDays(1)).effectiveTo(START.plusDays(1))
                .occupancyThreshold(new BigDecimal("0.5")).multiplier(new BigDecimal("1.50")).build());
        RateCard rateCard = rateCard(lot);

        DemandPricingService.DemandAdjustment adjustment = demandPricingService.resolve(
                lot.getId(), "CAR", START, START.plusHours(1), UUID.randomUUID(), rateCard);

        assertEquals(new BigDecimal("15000.0000"), adjustment.rateCard().getHourlyRate().setScale(4));
        assertEquals("0.5000", adjustment.metric());
    }

    @Test
    void leavesRateUnchangedBelowThreshold() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("Lot").timezone("Asia/Jakarta").build());
        slotRepository.saveAndFlush(slot(lot, "A-01"));
        ruleRepository.saveAndFlush(DemandPricingRule.builder().lot(lot).vehicleType("CAR")
                .effectiveFrom(START.minusDays(1)).effectiveTo(START.plusDays(1))
                .occupancyThreshold(new BigDecimal("0.8")).multiplier(new BigDecimal("1.50")).build());

        DemandPricingService.DemandAdjustment adjustment = demandPricingService.resolve(
                lot.getId(), "CAR", START, START.plusHours(1), UUID.randomUUID(), rateCard(lot));

        assertEquals(new BigDecimal("10000.00"), adjustment.rateCard().getHourlyRate());
        assertEquals("0.0000", adjustment.metric());
    }

    private ParkingSlot slot(ParkingLot lot, String slotId) {
        return ParkingSlot.builder().lot(lot).slotId(slotId).vehicleType("CAR").floor(1).build();
    }

    private RateCard rateCard(ParkingLot lot) {
        return RateCard.builder().lot(lot).vehicleType("CAR").version(1)
                .hourlyRate(new BigDecimal("10000.00")).dailyCap(new BigDecimal("30000.00"))
                .overnightSurcharge(BigDecimal.ZERO).effectiveFrom(START.minusDays(1)).build();
    }
}
