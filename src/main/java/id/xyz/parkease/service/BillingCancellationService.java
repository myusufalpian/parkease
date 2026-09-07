package id.xyz.parkease.service;

import id.xyz.parkease.domain.BillingOperation;
import id.xyz.parkease.domain.BillingOperation.OperationType;
import id.xyz.parkease.domain.OutboxEvent;
import id.xyz.parkease.domain.OutboxEvent.EventType;
import id.xyz.parkease.domain.ParkingInvoice;
import id.xyz.parkease.domain.ParkingInvoice.PaymentStatus;
import id.xyz.parkease.domain.Refund;
import id.xyz.parkease.domain.Reservation;
import id.xyz.parkease.domain.Reservation.Status;
import id.xyz.parkease.dto.CancellationResponse;
import id.xyz.parkease.dto.ReservationResponse;
import id.xyz.parkease.exception.ConflictException;
import id.xyz.parkease.exception.ResourceNotFoundException;
import id.xyz.parkease.mapper.ReservationMapper;
import id.xyz.parkease.mapper.PaymentReferenceSnapshotMapper;
import id.xyz.parkease.repository.BillingOperationRepository;
import id.xyz.parkease.repository.OutboxEventRepository;
import id.xyz.parkease.repository.ParkingInvoiceRepository;
import id.xyz.parkease.repository.RefundRepository;
import id.xyz.parkease.repository.ReservationRepository;
import id.xyz.parkease.service.CancellationFeeCalculator.CancellationFeeBreakdown;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BillingCancellationService {

    private static final String EMPTY_PAYLOAD = "{}";

    private final ReservationService reservationService;
    private final ReservationRepository reservationRepository;
    private final ParkingInvoiceRepository parkingInvoiceRepository;
    private final RefundRepository refundRepository;
    private final BillingOperationRepository billingOperationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final CancellationFeeCalculator cancellationFeeCalculator;
    private final ReservationMapper reservationMapper;
    private final PaymentReferenceSnapshotMapper paymentReferenceSnapshotMapper;
    private final AuditService auditService;
    private final Clock clock;

    public BillingCancellationService(
            ReservationService reservationService,
            ReservationRepository reservationRepository,
            ParkingInvoiceRepository parkingInvoiceRepository,
            RefundRepository refundRepository,
            BillingOperationRepository billingOperationRepository,
            OutboxEventRepository outboxEventRepository,
            CancellationFeeCalculator cancellationFeeCalculator,
            ReservationMapper reservationMapper,
            PaymentReferenceSnapshotMapper paymentReferenceSnapshotMapper,
            AuditService auditService,
            Clock clock) {
        this.reservationService = Objects.requireNonNull(reservationService);
        this.reservationRepository = Objects.requireNonNull(reservationRepository);
        this.parkingInvoiceRepository = Objects.requireNonNull(parkingInvoiceRepository);
        this.refundRepository = Objects.requireNonNull(refundRepository);
        this.billingOperationRepository = Objects.requireNonNull(billingOperationRepository);
        this.outboxEventRepository = Objects.requireNonNull(outboxEventRepository);
        this.cancellationFeeCalculator = Objects.requireNonNull(cancellationFeeCalculator);
        this.reservationMapper = Objects.requireNonNull(reservationMapper);
        this.paymentReferenceSnapshotMapper = Objects.requireNonNull(paymentReferenceSnapshotMapper);
        this.auditService = Objects.requireNonNull(auditService);
        this.clock = Objects.requireNonNull(clock);
    }

    public CancellationResponse cancel(UUID reservationId, String reason) {
        return cancel(reservationId, null, reason, currentTime());
    }

    public CancellationResponse cancel(UUID reservationId, String reason, OffsetDateTime requestedAt) {
        return cancel(reservationId, null, reason, requestedAt);
    }

    public CancellationResponse cancel(UUID reservationId, UUID actorId, String reason) {
        return cancel(reservationId, actorId, reason, currentTime());
    }

    public CancellationResponse cancel(UUID reservationId, UUID actorId, String reason, OffsetDateTime requestedAt) {
        BillingOperation existing = billingOperationRepository
                .findByReservation_IdAndOperationType(reservationId, OperationType.CANCELLATION)
                .orElse(null);
        if (existing != null) {
            return existingCancellationResult(reservationId);
        }

        Reservation before = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("reservation was not found"));
        boolean wasCancellable = before.getStatus() == Status.PENDING || before.getStatus() == Status.ACTIVE;

        ReservationResponse reservationResponse = reservationService.cancel(reservationId, reason, requestedAt);
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("reservation was not found"));
        boolean transitionedToCancelled = wasCancellable && reservation.getStatus() == Status.CANCELLED;

        ParkingInvoice invoice = parkingInvoiceRepository.findByReservation_Id(reservationId).orElse(null);
        Refund refund = null;
        if (transitionedToCancelled && invoice != null && invoice.getPaymentStatus() == PaymentStatus.PAID) {
            refund = createRefund(invoice, requestedAt);
        }

        recordCancellationOperation(reservation, reservationId);
        auditService.record(
                actorId,
                "CANCELLATION",
                "reservation:" + reservationId,
                reason,
                "status:" + before.getStatus(),
                "status:" + reservation.getStatus() + ";refund:" + refundStatusOf(refund));
        return new CancellationResponse(reservationResponse, refundStatusOf(refund), feeOf(refund), amountOf(refund));
    }

    private Refund createRefund(ParkingInvoice invoice, OffsetDateTime requestedAt) {
        CancellationFeeBreakdown breakdown = cancellationFeeCalculator.calculate(invoice.getTotal());
        String paymentTransactionId = captureReference(invoice.getReservation().getId());
        Refund refund = Refund.builder()
                .invoice(invoice)
                .paymentTransactionId(paymentTransactionId)
                .feeAmount(breakdown.cancellationFee())
                .refundAmount(breakdown.refundAmount())
                .build();
        Refund savedRefund = refundRepository.saveAndFlush(refund);

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .eventType(EventType.REFUND)
                .aggregateId(invoice.getId())
                .payloadJson(EMPTY_PAYLOAD)
                .build();
        outboxEventRepository.save(outboxEvent);
        return savedRefund;
    }

    private String captureReference(UUID reservationId) {
        return billingOperationRepository
                .findByReservation_IdAndOperationType(reservationId, OperationType.PAYMENT)
                .map(operation -> paymentReferenceSnapshotMapper.fromJson(operation.getResponseSnapshotJson()).providerReference())
                .orElseThrow(() -> new ConflictException("no captured payment reference exists for this reservation"));
    }

    private void recordCancellationOperation(Reservation reservation, UUID reservationId) {
        BillingOperation operation = BillingOperation.builder()
                .reservation(reservation)
                .operationType(OperationType.CANCELLATION)
                .idempotencyKey("CANCELLATION-" + reservationId)
                .build();
        billingOperationRepository.save(operation);
    }

    private CancellationResponse existingCancellationResult(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("reservation was not found"));
        ReservationResponse reservationResponse = reservationMapper.toResponse(reservation);
        Refund refund = parkingInvoiceRepository.findByReservation_Id(reservationId)
                .flatMap(invoice -> refundRepository.findByInvoice_Id(invoice.getId()))
                .orElse(null);
        return new CancellationResponse(reservationResponse, refundStatusOf(refund), feeOf(refund), amountOf(refund));
    }

    private String refundStatusOf(Refund refund) {
        return refund == null ? null : refund.getStatus().name();
    }

    private BigDecimal feeOf(Refund refund) {
        return refund == null ? null : refund.getFeeAmount();
    }

    private BigDecimal amountOf(Refund refund) {
        return refund == null ? null : refund.getRefundAmount();
    }

    private OffsetDateTime currentTime() {
        return OffsetDateTime.now(clock);
    }
}
