package id.xyz.parkease.service;

import id.xyz.parkease.config.BillingProperties;
import id.xyz.parkease.domain.ParkingInvoice;
import id.xyz.parkease.domain.ParkingInvoice.PaymentStatus;
import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.domain.Reservation;
import id.xyz.parkease.domain.Reservation.Status;
import id.xyz.parkease.repository.ParkingInvoiceRepository;
import id.xyz.parkease.repository.ParkingSlotRepository;
import id.xyz.parkease.repository.ReservationRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentTimeoutReconciler {

    private static final long RECONCILE_INTERVAL_MS = 60_000L;
    private static final long SECONDS_PER_MINUTE = 60L;

    private final ParkingInvoiceRepository parkingInvoiceRepository;
    private final ReservationRepository reservationRepository;
    private final ParkingSlotRepository parkingSlotRepository;
    private final BillingProperties billingProperties;
    private final Clock clock;

    public PaymentTimeoutReconciler(
            ParkingInvoiceRepository parkingInvoiceRepository,
            ReservationRepository reservationRepository,
            ParkingSlotRepository parkingSlotRepository,
            BillingProperties billingProperties,
            Clock clock) {
        this.parkingInvoiceRepository = Objects.requireNonNull(parkingInvoiceRepository);
        this.reservationRepository = Objects.requireNonNull(reservationRepository);
        this.parkingSlotRepository = Objects.requireNonNull(parkingSlotRepository);
        this.billingProperties = Objects.requireNonNull(billingProperties);
        this.clock = Objects.requireNonNull(clock);
    }

    @Scheduled(fixedDelay = RECONCILE_INTERVAL_MS)
    @Transactional
    public void expireTimedOutHolds() {
        OffsetDateTime cutoff = OffsetDateTime.now(clock)
                .minusSeconds(billingProperties.paymentTimeoutMinutes() * SECONDS_PER_MINUTE);
        List<ParkingInvoice> timedOut = parkingInvoiceRepository
                .findByPaymentStatusAndGeneratedAtBefore(PaymentStatus.PAYMENT_PENDING, cutoff);
        for (ParkingInvoice invoice : timedOut) {
            int expired = parkingInvoiceRepository.markExpiredIfPending(invoice.getId());
            if (expired == 1) {
                releaseHold(invoice.getReservation());
            }
        }
    }

    private void releaseHold(Reservation reservation) {
        Reservation current = reservationRepository.findById(reservation.getId()).orElse(null);
        if (current == null || current.getStatus() != Status.PENDING) {
            return;
        }
        ParkingSlot releasedSlot = parkingSlotRepository.save(current.getSlot().markAvailable());
        reservationRepository.save(
                current.toBuilder().slot(releasedSlot).build().cancel("payment timed out", false));
    }
}
