package id.xyz.parkease.service;

import id.xyz.parkease.domain.ParkingLot;
import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.domain.PricingPromotion;
import id.xyz.parkease.domain.PromotionHold;
import id.xyz.parkease.domain.Reservation;
import id.xyz.parkease.repository.ParkingLotRepository;
import id.xyz.parkease.repository.ParkingSlotRepository;
import id.xyz.parkease.repository.PricingPromotionRepository;
import id.xyz.parkease.repository.PromotionHoldRepository;
import id.xyz.parkease.repository.ReservationRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PromotionService.class, PromotionServiceTest.TestBeans.class})
class PromotionServiceTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2024-01-15T09:00:00+07:00");

    @Autowired private PromotionService promotionService;
    @Autowired private PricingPromotionRepository promotionRepository;
    @Autowired private PromotionHoldRepository holdRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ParkingSlotRepository slotRepository;
    @Autowired private ParkingLotRepository lotRepository;

    @BeforeEach
    void clean() {
        holdRepository.deleteAll();
        reservationRepository.deleteAll();
        promotionRepository.deleteAll();
        slotRepository.deleteAll();
        lotRepository.deleteAll();
    }

    @Test
    void eligibilityUsesRequestedCodeWhenMultiplePromotionsMatch() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("Lot").timezone("Asia/Jakarta").build());
        PricingPromotion first = savePromotion("FIRST", lot);
        savePromotion("SECOND", lot);
        Reservation reservation = reservation(lot);

        PricingPromotion selected = promotionService.hold("SECOND", reservation, "CAR", lot.getId(), "CUSTOMER");

        assertEquals("SECOND", selected.getCode());
        assertEquals(0, promotionRepository.findByCode(first.getCode()).orElseThrow().getUsageCount());
        assertEquals(1, promotionRepository.findByCode(selected.getCode()).orElseThrow().getUsageCount());
    }

    @Test
    void releaseIsIdempotentAndRestoresQuotaOnce() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("Lot").timezone("Asia/Jakarta").build());
        savePromotion("LIMITED", lot);
        Reservation reservation = reservation(lot);
        promotionService.hold("LIMITED", reservation, "CAR", lot.getId(), "CUSTOMER");
        promotionService.release(reservation.getId());
        promotionService.release(reservation.getId());

        assertEquals(0, promotionRepository.findByCode("LIMITED").orElseThrow().getUsageCount());
        assertEquals(PromotionHold.HoldStatus.RELEASED, holdRepository.findByReservation_Id(reservation.getId()).orElseThrow().getStatus());
    }

    @Test
    void exhaustedPromotionIsRejected() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("Lot").timezone("Asia/Jakarta").build());
        savePromotion("LIMITED", lot);
        promotionRepository.claim("LIMITED", PricingPromotion.PromoStatus.ACTIVE, NOW);
        Reservation reservation = reservation(lot);

        assertThrows(RuntimeException.class, () -> promotionService.hold("LIMITED", reservation, "CAR", lot.getId(), "CUSTOMER"));
    }

    private PricingPromotion savePromotion(String code, ParkingLot lot) {
        return promotionRepository.saveAndFlush(PricingPromotion.builder()
                .code(code).lot(lot).vehicleType("CAR").customerType("CUSTOMER")
                .effectiveFrom(NOW.minusDays(1)).effectiveTo(NOW.plusDays(1))
                .discountType(PricingPromotion.DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.TEN).usageLimit(1).build());
    }

    private Reservation reservation(ParkingLot lot) {
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId(UUID.randomUUID().toString()).vehicleType("CAR").floor(1).build());
        return reservationRepository.saveAndFlush(Reservation.builder().slot(slot).customerPlate("B1234").plannedStart(NOW).plannedEnd(NOW.plusHours(1)).build());
    }

    @TestConfiguration
    static class TestBeans {
        @Bean Clock fixedClock() { return Clock.fixed(Instant.parse("2024-01-15T02:00:00Z"), ZoneOffset.UTC); }
    }
}
