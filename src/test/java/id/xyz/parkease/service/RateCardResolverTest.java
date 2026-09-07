package id.xyz.parkease.service;

import id.xyz.parkease.domain.ParkingLot;
import id.xyz.parkease.domain.RateCard;
import id.xyz.parkease.domain.RateCard.RateCardStatus;
import id.xyz.parkease.exception.BusinessValidationException;
import id.xyz.parkease.repository.ParkingLotRepository;
import id.xyz.parkease.repository.RateCardRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RateCardResolver.class)
class RateCardResolverTest {

    private static final String VEHICLE_CAR = "CAR";
    private static final OffsetDateTime BOOKING_TIME = OffsetDateTime.parse("2024-01-20T09:00:00+07:00");

    @Autowired
    private RateCardResolver rateCardResolver;

    @Autowired
    private ParkingLotRepository parkingLotRepository;

    @Autowired
    private RateCardRepository rateCardRepository;

    private ParkingLot lot;

    @BeforeEach
    void setUp() {
        rateCardRepository.deleteAll();
        parkingLotRepository.deleteAll();
        lot = parkingLotRepository.saveAndFlush(
                ParkingLot.builder().name("Lot").timezone("Asia/Jakarta").build());
    }

    private RateCard.RateCardBuilder baseCard() {
        return RateCard.builder()
                .lot(lot)
                .vehicleType(VEHICLE_CAR)
                .hourlyRate(new BigDecimal("10000.00"))
                .dailyCap(new BigDecimal("30000.00"))
                .overnightSurcharge(new BigDecimal("15000.00"))
                .currency("IDR");
    }

    @Test
    void resolvesActiveEffectiveRateCard() {
        rateCardRepository.saveAndFlush(baseCard()
                .version(1)
                .effectiveFrom(OffsetDateTime.parse("2024-01-01T00:00:00+07:00"))
                .build());

        RateCard resolved = rateCardResolver.resolve(lot.getId(), VEHICLE_CAR, BOOKING_TIME);

        assertEquals(1, resolved.getVersion());
    }

    @Test
    void picksHighestVersionAmongEffectiveCards() {
        rateCardRepository.saveAndFlush(baseCard()
                .version(1)
                .effectiveFrom(OffsetDateTime.parse("2024-01-01T00:00:00+07:00"))
                .build());
        rateCardRepository.saveAndFlush(baseCard()
                .version(2)
                .effectiveFrom(OffsetDateTime.parse("2024-01-10T00:00:00+07:00"))
                .build());

        RateCard resolved = rateCardResolver.resolve(lot.getId(), VEHICLE_CAR, BOOKING_TIME);

        assertEquals(2, resolved.getVersion());
    }

    @Test
    void rejectsWhenNoRateCardConfigured() {
        assertThrows(
                BusinessValidationException.class,
                () -> rateCardResolver.resolve(lot.getId(), VEHICLE_CAR, BOOKING_TIME));
    }

    @Test
    void rejectsWhenRateCardNotYetEffective() {
        rateCardRepository.saveAndFlush(baseCard()
                .version(1)
                .effectiveFrom(OffsetDateTime.parse("2024-06-01T00:00:00+07:00"))
                .build());

        assertThrows(
                BusinessValidationException.class,
                () -> rateCardResolver.resolve(lot.getId(), VEHICLE_CAR, BOOKING_TIME));
    }

    @Test
    void rejectsWhenOnlyRetiredCardMatches() {
        rateCardRepository.saveAndFlush(baseCard()
                .version(1)
                .status(RateCardStatus.RETIRED)
                .effectiveFrom(OffsetDateTime.parse("2024-01-01T00:00:00+07:00"))
                .build());

        assertThrows(
                BusinessValidationException.class,
                () -> rateCardResolver.resolve(lot.getId(), VEHICLE_CAR, BOOKING_TIME));
    }

    @Test
    void rejectsForUnknownLot() {
        assertThrows(
                BusinessValidationException.class,
                () -> rateCardResolver.resolve(UUID.randomUUID(), VEHICLE_CAR, BOOKING_TIME));
    }
}
