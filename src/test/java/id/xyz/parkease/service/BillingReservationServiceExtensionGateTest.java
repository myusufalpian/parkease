package id.xyz.parkease.service;

import id.xyz.parkease.domain.ExtensionCharge;
import id.xyz.parkease.domain.ExtensionCharge.ExtensionPaymentStatus;
import id.xyz.parkease.domain.ParkingLot;
import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.domain.Reservation;
import id.xyz.parkease.domain.Reservation.Status;
import id.xyz.parkease.exception.ConflictException;
import id.xyz.parkease.mapper.InvoiceMapper;
import id.xyz.parkease.mapper.PricingSnapshotMapper;
import id.xyz.parkease.mapper.ReservationMapper;
import id.xyz.parkease.repository.ExtensionChargeRepository;
import id.xyz.parkease.repository.ParkingLotRepository;
import id.xyz.parkease.repository.ParkingSlotRepository;
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
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        BillingReservationService.class,
        ReservationService.class,
        RateCardResolver.class,
        BillingCalculator.class,
        AuditService.class,
        ReservationMapper.class,
        InvoiceMapper.class,
        PricingSnapshotMapper.class,
        BillingReservationServiceExtensionGateTest.TestBeans.class})
class BillingReservationServiceExtensionGateTest {

    @Autowired
    private BillingReservationService billingReservationService;

    @Autowired
    private ParkingLotRepository parkingLotRepository;

    @Autowired
    private ParkingSlotRepository parkingSlotRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ExtensionChargeRepository extensionChargeRepository;

    @BeforeEach
    void cleanDatabase() {
        extensionChargeRepository.deleteAll();
        reservationRepository.deleteAll();
        parkingSlotRepository.deleteAll();
        parkingLotRepository.deleteAll();
    }

    private Reservation activeReservationWithExtension(ExtensionPaymentStatus status) {
        ParkingLot lot = parkingLotRepository.saveAndFlush(
                ParkingLot.builder().name("Lot").timezone("Asia/Jakarta").build());
        ParkingSlot slot = parkingSlotRepository.saveAndFlush(
                ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        Reservation reservation = reservationRepository.saveAndFlush(Reservation.builder()
                .slot(slot)
                .plannedStart(OffsetDateTime.parse("2024-01-15T09:00:00+07:00"))
                .plannedEnd(OffsetDateTime.parse("2024-01-15T10:00:00+07:00"))
                .status(Status.ACTIVE)
                .actualStart(OffsetDateTime.parse("2024-01-15T09:00:00+07:00"))
                .build());
        extensionChargeRepository.saveAndFlush(ExtensionCharge.builder()
                .reservation(reservation)
                .additionalDurationMinutes(30)
                .amount(new BigDecimal("5000.00"))
                .currency("IDR")
                .paymentStatus(status)
                .build());
        return reservation;
    }

    @Test
    void checkOutIsBlockedWhileAnExtensionChargeIsUnpaid() {
        UUID reservationId = activeReservationWithExtension(ExtensionPaymentStatus.PENDING).getId();

        assertThrows(ConflictException.class, () -> billingReservationService.checkOutIdempotent(reservationId));
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2024-01-15T09:30:00Z"), ZoneOffset.UTC);
        }
    }
}
