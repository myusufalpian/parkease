package id.xyz.parkease.repository;

import id.xyz.parkease.domain.ParkingInvoice;
import id.xyz.parkease.domain.ParkingInvoice.PaymentStatus;
import id.xyz.parkease.domain.ParkingLot;
import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.domain.Reservation;
import id.xyz.parkease.domain.Reservation.Status;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ParkingInvoiceRepositoryTest {

    private static final OffsetDateTime PAID_AT = OffsetDateTime.parse("2024-01-15T09:05:00Z");

    @Autowired
    private ParkingLotRepository parkingLotRepository;

    @Autowired
    private ParkingSlotRepository parkingSlotRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ParkingInvoiceRepository parkingInvoiceRepository;

    @BeforeEach
    void cleanDatabase() {
        parkingInvoiceRepository.deleteAll();
        reservationRepository.deleteAll();
        parkingSlotRepository.deleteAll();
        parkingLotRepository.deleteAll();
    }

    private ParkingInvoice pendingInvoice() {
        ParkingLot lot = parkingLotRepository.saveAndFlush(
                ParkingLot.builder().name("Lot").timezone("Asia/Jakarta").build());
        ParkingSlot slot = parkingSlotRepository.saveAndFlush(
                ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        Reservation reservation = reservationRepository.saveAndFlush(Reservation.builder()
                .slot(slot)
                .plannedStart(OffsetDateTime.parse("2024-01-15T09:00:00+07:00"))
                .plannedEnd(OffsetDateTime.parse("2024-01-15T10:00:00+07:00"))
                .status(Status.PENDING)
                .build());
        return parkingInvoiceRepository.saveAndFlush(ParkingInvoice.builder()
                .reservation(reservation)
                .durationMinutes(60)
                .subtotal(new BigDecimal("10000.00"))
                .total(new BigDecimal("10000.00"))
                .currency("IDR")
                .build());
    }

    @Test
    void markPaidIfPendingUpdatesExactlyOncePendingInvoice() {
        ParkingInvoice invoice = pendingInvoice();

        assertEquals(1, parkingInvoiceRepository.markPaidIfPending(invoice.getId(), PAID_AT));
        assertEquals(PaymentStatus.PAID, parkingInvoiceRepository.findById(invoice.getId()).orElseThrow().getPaymentStatus());
    }

    @Test
    void expireCannotOverwriteAnAlreadyPaidInvoice() {
        ParkingInvoice invoice = pendingInvoice();
        parkingInvoiceRepository.markPaidIfPending(invoice.getId(), PAID_AT);

        assertEquals(0, parkingInvoiceRepository.markExpiredIfPending(invoice.getId()));
        assertEquals(PaymentStatus.PAID, parkingInvoiceRepository.findById(invoice.getId()).orElseThrow().getPaymentStatus());
    }

    @Test
    void captureCannotOverwriteAnAlreadyExpiredInvoice() {
        ParkingInvoice invoice = pendingInvoice();
        parkingInvoiceRepository.markExpiredIfPending(invoice.getId());

        assertEquals(0, parkingInvoiceRepository.markPaidIfPending(invoice.getId(), PAID_AT));
        assertEquals(PaymentStatus.EXPIRED, parkingInvoiceRepository.findById(invoice.getId()).orElseThrow().getPaymentStatus());
    }
}
