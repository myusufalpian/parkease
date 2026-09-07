package id.xyz.parkease.service;

import id.xyz.parkease.domain.OutboxEvent;
import id.xyz.parkease.domain.OutboxEvent.EventType;
import id.xyz.parkease.domain.OutboxEvent.OutboxStatus;
import id.xyz.parkease.domain.BillingOperation;
import id.xyz.parkease.domain.BillingOperation.OperationType;
import id.xyz.parkease.domain.ExtensionCharge;
import id.xyz.parkease.domain.ExtensionCharge.ExtensionPaymentStatus;
import id.xyz.parkease.domain.ParkingInvoice;
import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.domain.Refund;
import id.xyz.parkease.domain.Reservation;
import id.xyz.parkease.domain.Reservation.Status;
import id.xyz.parkease.dto.PaymentReferenceSnapshot;
import id.xyz.parkease.mapper.PaymentReferenceSnapshotMapper;
import id.xyz.parkease.repository.BillingOperationRepository;
import id.xyz.parkease.repository.ExtensionChargeRepository;
import id.xyz.parkease.repository.OutboxEventRepository;
import id.xyz.parkease.repository.ParkingInvoiceRepository;
import id.xyz.parkease.repository.ParkingSlotRepository;
import id.xyz.parkease.repository.RefundRepository;
import id.xyz.parkease.repository.ReservationRepository;
import id.xyz.parkease.service.PaymentAdapter.PaymentResult;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxDispatcher {

    private final OutboxEventRepository outboxEventRepository;
    private final ParkingInvoiceRepository parkingInvoiceRepository;
    private final RefundRepository refundRepository;
    private final ReservationRepository reservationRepository;
    private final ParkingSlotRepository parkingSlotRepository;
    private final ExtensionChargeRepository extensionChargeRepository;
    private final BillingOperationRepository billingOperationRepository;
    private final PaymentReferenceSnapshotMapper paymentReferenceSnapshotMapper;
    private final PaymentAdapter paymentAdapter;
    private final Clock clock;

    public OutboxDispatcher(
            OutboxEventRepository outboxEventRepository,
            ParkingInvoiceRepository parkingInvoiceRepository,
            RefundRepository refundRepository,
            ReservationRepository reservationRepository,
            ParkingSlotRepository parkingSlotRepository,
            ExtensionChargeRepository extensionChargeRepository,
            BillingOperationRepository billingOperationRepository,
            PaymentReferenceSnapshotMapper paymentReferenceSnapshotMapper,
            PaymentAdapter paymentAdapter,
            Clock clock) {
        this.outboxEventRepository = Objects.requireNonNull(outboxEventRepository);
        this.parkingInvoiceRepository = Objects.requireNonNull(parkingInvoiceRepository);
        this.refundRepository = Objects.requireNonNull(refundRepository);
        this.reservationRepository = Objects.requireNonNull(reservationRepository);
        this.parkingSlotRepository = Objects.requireNonNull(parkingSlotRepository);
        this.extensionChargeRepository = Objects.requireNonNull(extensionChargeRepository);
        this.billingOperationRepository = Objects.requireNonNull(billingOperationRepository);
        this.paymentReferenceSnapshotMapper = Objects.requireNonNull(paymentReferenceSnapshotMapper);
        this.paymentAdapter = Objects.requireNonNull(paymentAdapter);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public void dispatch(OutboxEvent event) {
        if (event.getStatus() != OutboxStatus.PENDING) {
            return;
        }
        if (event.getEventType() == EventType.PAYMENT_CAPTURE) {
            dispatchPaymentCapture(event);
        } else if (event.getEventType() == EventType.EXTENSION_PAYMENT) {
            dispatchExtensionPayment(event);
        } else {
            dispatchRefund(event);
        }
    }

    public void dispatchPendingFor(UUID aggregateId) {
        List<OutboxEvent> pending =
                outboxEventRepository.findByAggregateIdAndStatus(aggregateId, OutboxStatus.PENDING);
        for (OutboxEvent event : pending) {
            dispatch(event);
        }
    }

    private void dispatchPaymentCapture(OutboxEvent event) {
        ParkingInvoice invoice = parkingInvoiceRepository.findByReservation_Id(event.getAggregateId())
                .orElseThrow(() -> new IllegalStateException("invoice was not found for outbox event " + event.getId()));
        PaymentResult result = paymentAdapter.capture(
                event.getAggregateId(), invoice.getTotal(), invoice.getCurrency(), event.getId().toString());
        OffsetDateTime now = currentTime();
        if (result.successful()) {
            int updated = parkingInvoiceRepository.markPaidIfPending(invoice.getId(), now);
            if (updated == 1) {
                recordCaptureReference(invoice.getReservation(), result.providerReference());
                outboxEventRepository.save(event.markProcessed(now));
            } else {
                outboxEventRepository.save(event.markFailed("invoice no longer pending at capture", now));
            }
        } else {
            parkingInvoiceRepository.save(invoice.markFailed());
            releaseHold(event.getAggregateId());
            outboxEventRepository.save(event.markFailed(result.failureReason(), now));
        }
    }

    private void recordCaptureReference(Reservation reservation, String providerReference) {
        billingOperationRepository.save(BillingOperation.builder()
                .reservation(reservation)
                .operationType(OperationType.PAYMENT)
                .idempotencyKey("PAYMENT-" + reservation.getId())
                .responseSnapshotJson(paymentReferenceSnapshotMapper.toJson(new PaymentReferenceSnapshot(providerReference)))
                .build());
    }

    private void dispatchExtensionPayment(OutboxEvent event) {
        ExtensionCharge charge = extensionChargeRepository.findById(event.getAggregateId())
                .orElseThrow(() -> new IllegalStateException("extension charge was not found for outbox event " + event.getId()));
        PaymentResult result = paymentAdapter.capture(
                charge.getReservation().getId(), charge.getAmount(), charge.getCurrency(), event.getId().toString());
        OffsetDateTime now = currentTime();
        if (result.successful()) {
            extensionChargeRepository.save(charge.markPaid());
            outboxEventRepository.save(event.markProcessed(now));
        } else {
            extensionChargeRepository.save(charge.markFailed());
            outboxEventRepository.save(event.markFailed(result.failureReason(), now));
        }
    }

    private void dispatchRefund(OutboxEvent event) {
        Refund refund = refundRepository.findByInvoice_Id(event.getAggregateId())
                .orElseThrow(() -> new IllegalStateException("refund was not found for outbox event " + event.getId()));
        OffsetDateTime now = currentTime();
        refundRepository.save(refund.markProcessing(now));
        PaymentResult result = paymentAdapter.refund(
                refund.getPaymentTransactionId(), refund.getRefundAmount(), refund.getInvoice().getCurrency(), event.getId().toString());
        if (result.successful()) {
            refundRepository.save(refund.markSucceeded(result.providerReference(), now));
            outboxEventRepository.save(event.markProcessed(now));
        } else {
            refundRepository.save(refund.markFailed(now));
            outboxEventRepository.save(event.markFailed(result.failureReason(), now));
        }
    }

    private void releaseHold(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null || reservation.getStatus() != Status.PENDING) {
            return;
        }
        ParkingSlot releasedSlot = parkingSlotRepository.save(reservation.getSlot().markAvailable());
        reservationRepository.save(
                reservation.toBuilder().slot(releasedSlot).build().cancel("payment failed", false));
    }

    private OffsetDateTime currentTime() {
        return OffsetDateTime.now(clock);
    }
}
