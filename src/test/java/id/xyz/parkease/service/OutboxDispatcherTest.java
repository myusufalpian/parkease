package id.xyz.parkease.service;

import id.xyz.parkease.domain.OutboxEvent;
import id.xyz.parkease.domain.OutboxEvent.EventType;
import id.xyz.parkease.domain.OutboxEvent.OutboxStatus;
import id.xyz.parkease.domain.ParkingInvoice;
import id.xyz.parkease.domain.ParkingInvoice.PaymentStatus;
import id.xyz.parkease.domain.ParkingLot;
import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.domain.Refund;
import id.xyz.parkease.domain.Refund.RefundStatus;
import id.xyz.parkease.domain.Reservation;
import id.xyz.parkease.domain.Reservation.Status;
import id.xyz.parkease.mapper.PaymentReferenceSnapshotMapper;
import id.xyz.parkease.repository.BillingOperationRepository;
import id.xyz.parkease.repository.ExtensionChargeRepository;
import id.xyz.parkease.repository.OutboxEventRepository;
import id.xyz.parkease.repository.ParkingInvoiceRepository;
import id.xyz.parkease.repository.ParkingLotRepository;
import id.xyz.parkease.repository.ParkingSlotRepository;
import id.xyz.parkease.repository.RefundRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxDispatcherTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2024-01-15T08:00:00Z"), ZoneOffset.UTC);
    private static final String CURRENCY = "IDR";

    @Autowired
    private ParkingLotRepository parkingLotRepository;

    @Autowired
    private ParkingSlotRepository parkingSlotRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ParkingInvoiceRepository parkingInvoiceRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ExtensionChargeRepository extensionChargeRepository;

    @Autowired
    private BillingOperationRepository billingOperationRepository;

    private static final class StubPaymentAdapter implements PaymentAdapter {
        private final boolean succeed;

        StubPaymentAdapter(boolean succeed) {
            this.succeed = succeed;
        }

        @Override
        public PaymentResult capture(UUID reservationId, BigDecimal amount, String currency, String idempotencyKey) {
            return new PaymentResult(succeed, succeed ? "REF-" + idempotencyKey : null, succeed ? null : "declined");
        }

        @Override
        public PaymentResult refund(String paymentTransactionId, BigDecimal amount, String currency, String idempotencyKey) {
            return new PaymentResult(succeed, succeed ? "REF-" + idempotencyKey : null, succeed ? null : "declined");
        }
    }

    @BeforeEach
    void cleanDatabase() {
        outboxEventRepository.deleteAll();
        billingOperationRepository.deleteAll();
        extensionChargeRepository.deleteAll();
        refundRepository.deleteAll();
        parkingInvoiceRepository.deleteAll();
        reservationRepository.deleteAll();
        parkingSlotRepository.deleteAll();
        parkingLotRepository.deleteAll();
    }

    private OutboxDispatcher dispatcher(boolean paymentSucceeds) {
        return new OutboxDispatcher(
                outboxEventRepository,
                parkingInvoiceRepository,
                refundRepository,
                reservationRepository,
                parkingSlotRepository,
                extensionChargeRepository,
                billingOperationRepository,
                new PaymentReferenceSnapshotMapper(),
                new StubPaymentAdapter(paymentSucceeds),
                FIXED_CLOCK);
    }

    private Reservation reservation() {
        ParkingLot lot = parkingLotRepository.saveAndFlush(
                ParkingLot.builder().name("Lot").timezone("Asia/Jakarta").build());
        ParkingSlot slot = parkingSlotRepository.saveAndFlush(
                ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        return reservationRepository.saveAndFlush(Reservation.builder()
                .slot(slot)
                .plannedStart(OffsetDateTime.parse("2024-01-15T09:00:00+07:00"))
                .plannedEnd(OffsetDateTime.parse("2024-01-15T10:00:00+07:00"))
                .status(Status.PENDING)
                .build());
    }

    private ParkingInvoice invoice(Reservation reservation) {
        return parkingInvoiceRepository.saveAndFlush(ParkingInvoice.builder()
                .reservation(reservation)
                .durationMinutes(60)
                .subtotal(new BigDecimal("10000.00"))
                .total(new BigDecimal("10000.00"))
                .currency(CURRENCY)
                .build());
    }

    @Test
    void paymentCaptureSuccessMarksInvoicePaidAndEventProcessed() {
        Reservation reservation = reservation();
        ParkingInvoice invoice = invoice(reservation);
        OutboxEvent event = outboxEventRepository.saveAndFlush(OutboxEvent.builder()
                .eventType(EventType.PAYMENT_CAPTURE)
                .aggregateId(reservation.getId())
                .payloadJson("{}")
                .build());

        dispatcher(true).dispatch(event);

        assertEquals(PaymentStatus.PAID, parkingInvoiceRepository.findById(invoice.getId()).orElseThrow().getPaymentStatus());
        assertEquals(OutboxStatus.PROCESSED, outboxEventRepository.findById(event.getId()).orElseThrow().getStatus());
    }

    @Test
    void paymentCaptureFailureMarksInvoiceFailedAndEventFailed() {
        Reservation reservation = reservation();
        ParkingInvoice invoice = invoice(reservation);
        OutboxEvent event = outboxEventRepository.saveAndFlush(OutboxEvent.builder()
                .eventType(EventType.PAYMENT_CAPTURE)
                .aggregateId(reservation.getId())
                .payloadJson("{}")
                .build());

        dispatcher(false).dispatch(event);

        assertEquals(PaymentStatus.FAILED, parkingInvoiceRepository.findById(invoice.getId()).orElseThrow().getPaymentStatus());
        assertEquals(OutboxStatus.FAILED, outboxEventRepository.findById(event.getId()).orElseThrow().getStatus());
    }

    @Test
    void refundSuccessMarksRefundSucceededAndEventProcessed() {
        Reservation reservation = reservation();
        ParkingInvoice invoice = invoice(reservation);
        Refund refund = refundRepository.saveAndFlush(Refund.builder()
                .invoice(invoice)
                .paymentTransactionId("REFUND-" + invoice.getId())
                .feeAmount(new BigDecimal("1000.00"))
                .refundAmount(new BigDecimal("9000.00"))
                .build());
        OutboxEvent event = outboxEventRepository.saveAndFlush(OutboxEvent.builder()
                .eventType(EventType.REFUND)
                .aggregateId(invoice.getId())
                .payloadJson("{}")
                .build());

        dispatcher(true).dispatch(event);

        assertEquals(RefundStatus.SUCCEEDED, refundRepository.findById(refund.getId()).orElseThrow().getStatus());
        assertEquals(OutboxStatus.PROCESSED, outboxEventRepository.findById(event.getId()).orElseThrow().getStatus());
    }

    @Test
    void refundFailureMarksRefundFailedAndEventFailed() {
        Reservation reservation = reservation();
        ParkingInvoice invoice = invoice(reservation);
        Refund refund = refundRepository.saveAndFlush(Refund.builder()
                .invoice(invoice)
                .paymentTransactionId("REFUND-" + invoice.getId())
                .feeAmount(new BigDecimal("1000.00"))
                .refundAmount(new BigDecimal("9000.00"))
                .build());
        OutboxEvent event = outboxEventRepository.saveAndFlush(OutboxEvent.builder()
                .eventType(EventType.REFUND)
                .aggregateId(invoice.getId())
                .payloadJson("{}")
                .build());

        dispatcher(false).dispatch(event);

        assertEquals(RefundStatus.FAILED, refundRepository.findById(refund.getId()).orElseThrow().getStatus());
        assertEquals(OutboxStatus.FAILED, outboxEventRepository.findById(event.getId()).orElseThrow().getStatus());
    }
}
