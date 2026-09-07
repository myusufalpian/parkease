package id.xyz.parkease.service;

import id.xyz.parkease.domain.BillingOperation;
import id.xyz.parkease.domain.BillingOperation.OperationType;
import id.xyz.parkease.domain.CustomerAccount;
import id.xyz.parkease.domain.ExtensionCharge;
import id.xyz.parkease.domain.OutboxEvent;
import id.xyz.parkease.domain.OutboxEvent.EventType;
import id.xyz.parkease.domain.ParkingInvoice;
import id.xyz.parkease.domain.ParkingLot;
import id.xyz.parkease.domain.PricingPromotion;
import id.xyz.parkease.domain.RateCard;
import id.xyz.parkease.domain.Reservation;
import id.xyz.parkease.domain.ReservationOwner;
import id.xyz.parkease.dto.BillingBreakdown;
import id.xyz.parkease.dto.BookingResponse;
import id.xyz.parkease.dto.InvoiceResponse;
import id.xyz.parkease.dto.ReservationRequest;
import id.xyz.parkease.dto.ReservationResponse;
import id.xyz.parkease.exception.ConflictException;
import id.xyz.parkease.exception.ResourceNotFoundException;
import id.xyz.parkease.mapper.InvoiceMapper;
import id.xyz.parkease.mapper.PricingSnapshotMapper;
import id.xyz.parkease.mapper.ReservationMapper;
import id.xyz.parkease.repository.CustomerAccountRepository;
import id.xyz.parkease.repository.BillingOperationRepository;
import id.xyz.parkease.repository.ExtensionChargeRepository;
import id.xyz.parkease.repository.OutboxEventRepository;
import id.xyz.parkease.repository.ParkingInvoiceRepository;
import id.xyz.parkease.repository.ParkingLotRepository;
import id.xyz.parkease.repository.ReservationOwnerRepository;
import id.xyz.parkease.repository.ReservationRepository;
import id.xyz.parkease.security.AuthenticatedPrincipal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BillingReservationService {

    private static final String EMPTY_PAYLOAD = "{}";

    private final ReservationService reservationService;
    private final ParkingLotRepository parkingLotRepository;
    private final ReservationRepository reservationRepository;
    private final CustomerAccountRepository customerAccountRepository;
    private final ReservationOwnerRepository reservationOwnerRepository;
    private final ParkingInvoiceRepository parkingInvoiceRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final BillingOperationRepository billingOperationRepository;
    private final ExtensionChargeRepository extensionChargeRepository;
    private final RateCardResolver rateCardResolver;
    private final BillingCalculator billingCalculator;
    private final PricingSnapshotMapper pricingSnapshotMapper;
    private final ReservationMapper reservationMapper;
    private final InvoiceMapper invoiceMapper;
    private final AuditService auditService;
    private final PromotionService promotionService;
    private final DemandPricingService demandPricingService;
    private final Clock clock;

    public BillingReservationService(
            ReservationService reservationService,
            ParkingLotRepository parkingLotRepository,
            ReservationRepository reservationRepository,
            CustomerAccountRepository customerAccountRepository,
            ReservationOwnerRepository reservationOwnerRepository,
            ParkingInvoiceRepository parkingInvoiceRepository,
            OutboxEventRepository outboxEventRepository,
            BillingOperationRepository billingOperationRepository,
            ExtensionChargeRepository extensionChargeRepository,
            RateCardResolver rateCardResolver,
            BillingCalculator billingCalculator,
            PricingSnapshotMapper pricingSnapshotMapper,
            ReservationMapper reservationMapper,
            InvoiceMapper invoiceMapper,
            AuditService auditService,
            PromotionService promotionService,
            DemandPricingService demandPricingService,
            Clock clock) {
        this.reservationService = Objects.requireNonNull(reservationService);
        this.parkingLotRepository = Objects.requireNonNull(parkingLotRepository);
        this.reservationRepository = Objects.requireNonNull(reservationRepository);
        this.customerAccountRepository = Objects.requireNonNull(customerAccountRepository);
        this.reservationOwnerRepository = Objects.requireNonNull(reservationOwnerRepository);
        this.parkingInvoiceRepository = Objects.requireNonNull(parkingInvoiceRepository);
        this.outboxEventRepository = Objects.requireNonNull(outboxEventRepository);
        this.billingOperationRepository = Objects.requireNonNull(billingOperationRepository);
        this.extensionChargeRepository = Objects.requireNonNull(extensionChargeRepository);
        this.rateCardResolver = Objects.requireNonNull(rateCardResolver);
        this.billingCalculator = Objects.requireNonNull(billingCalculator);
        this.pricingSnapshotMapper = Objects.requireNonNull(pricingSnapshotMapper);
        this.reservationMapper = Objects.requireNonNull(reservationMapper);
        this.invoiceMapper = Objects.requireNonNull(invoiceMapper);
        this.auditService = Objects.requireNonNull(auditService);
        this.promotionService = Objects.requireNonNull(promotionService);
        this.demandPricingService = Objects.requireNonNull(demandPricingService);
        this.clock = Objects.requireNonNull(clock);
    }

    public BookingResponse book(ReservationRequest request, AuthenticatedPrincipal principal) {
        return book(request, principal, currentTime());
    }

    public BookingResponse book(ReservationRequest request, AuthenticatedPrincipal principal, OffsetDateTime requestedAt) {
        Objects.requireNonNull(principal, "principal must not be null");
        ReservationResponse reservationResponse = reservationService.createReservation(request, requestedAt);
        Reservation reservation = reservationRepository.findById(reservationResponse.id())
                .orElseThrow(() -> new ResourceNotFoundException("reservation was not found"));

        CustomerAccount owner = assignOwner(reservation, principal);
        ParkingLot lot = parkingLotRepository.findById(request.lotId())
                .orElseThrow(() -> new ResourceNotFoundException("parking lot was not found"));
        promotionService.hold(request.promoCode(), reservation, request.vehicleType(), lot.getId(), owner.getCustomerType());
        ParkingInvoice invoice = createInvoice(request, reservation, requestedAt);
        auditService.record(
                principal.customerAccountId(),
                "BOOKING",
                "reservation:" + reservation.getId(),
                "reservation booked",
                null,
                "invoice:" + invoice.getId() + ";status:" + invoice.getPaymentStatus());

        return new BookingResponse(reservationResponse, invoiceMapper.toResponse(invoice));
    }

    private CustomerAccount assignOwner(Reservation reservation, AuthenticatedPrincipal principal) {
        CustomerAccount owner = customerAccountRepository.findById(principal.customerAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("customer account was not found"));
        reservationOwnerRepository.save(ReservationOwner.builder()
                .reservation(reservation)
                .customerAccount(owner)
                .build());
        return owner;
    }

    private ParkingInvoice createInvoice(ReservationRequest request, Reservation reservation, OffsetDateTime requestedAt) {
        ParkingLot lot = parkingLotRepository.findById(request.lotId())
                .orElseThrow(() -> new ResourceNotFoundException("parking lot was not found"));
        ZoneId lotTimezone = ZoneId.of(lot.getTimezone());
        RateCard rateCard = rateCardResolver.resolve(request.lotId(), request.vehicleType(), requestedAt);
        DemandPricingService.DemandAdjustment demand = demandPricingService.resolve(
                request.lotId(), request.vehicleType(), request.plannedStart(), request.plannedEnd(), reservation.getId(), rateCard);
        BillingBreakdown breakdown = billingCalculator.calculate(
                request.plannedStart(), request.plannedEnd(), lotTimezone, demand.rateCard(), demand.metric());
        PricingPromotion promotion = promotionService.findHeld(reservation.getId());
        breakdown = billingCalculator.applyPromotion(breakdown, promotion);

        ParkingInvoice invoice = ParkingInvoice.builder()
                .reservation(reservation)
                .durationMinutes(breakdown.durationMinutes())
                .subtotal(breakdown.subtotal())
                .discountAmount(breakdown.discountAmount())
                .total(breakdown.total())
                .currency(breakdown.currency())
                .pricingSnapshotJson(pricingSnapshotMapper.toJson(breakdown.pricingSnapshot()))
                .build();
        ParkingInvoice savedInvoice = parkingInvoiceRepository.saveAndFlush(invoice);

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .eventType(EventType.PAYMENT_CAPTURE)
                .aggregateId(reservation.getId())
                .payloadJson(EMPTY_PAYLOAD)
                .build();
        outboxEventRepository.save(outboxEvent);

        return savedInvoice;
    }

    public InvoiceResponse getInvoice(UUID reservationId) {
        ParkingInvoice invoice = parkingInvoiceRepository.findByReservation_Id(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("invoice was not found for this reservation"));
        return invoiceMapper.toResponse(invoice);
    }

    public ReservationResponse checkInPaid(UUID reservationId) {
        requirePaidInvoice(reservationId);
        ReservationResponse response = reservationService.checkIn(reservationId);
        if (response.status() == Reservation.Status.NO_SHOW) {
            promotionService.release(reservationId);
        } else {
            promotionService.consume(reservationId);
        }
        return response;
    }

    public ReservationResponse checkOutIdempotent(UUID reservationId) {
        requireExtensionsPaid(reservationId);
        boolean alreadyRecorded = billingOperationRepository
                .findByReservation_IdAndOperationType(reservationId, OperationType.CHECKOUT)
                .isPresent();
        ReservationResponse response = reservationService.checkOut(reservationId);
        if (!alreadyRecorded) {
            Reservation reservation = reservationRepository.findById(reservationId)
                    .orElseThrow(() -> new ResourceNotFoundException("reservation was not found"));
            billingOperationRepository.saveAndFlush(BillingOperation.builder()
                    .reservation(reservation)
                    .operationType(OperationType.CHECKOUT)
                    .idempotencyKey("CHECKOUT-" + reservationId)
                    .build());
        }
        return response;
    }

    private void requireExtensionsPaid(UUID reservationId) {
        boolean hasUnpaidExtension = extensionChargeRepository.findByReservation_Id(reservationId).stream()
                .anyMatch(charge -> charge.getPaymentStatus() != ExtensionCharge.ExtensionPaymentStatus.PAID);
        if (hasUnpaidExtension) {
            throw new ConflictException("reservation has an unpaid extension charge and cannot be checked out");
        }
    }

    private void requirePaidInvoice(UUID reservationId) {
        ParkingInvoice invoice = parkingInvoiceRepository.findByReservation_Id(reservationId)
                .orElseThrow(() -> new ConflictException("reservation has no invoice and cannot be checked in"));
        if (invoice.getPaymentStatus() != ParkingInvoice.PaymentStatus.PAID) {
            throw new ConflictException("reservation invoice is not paid and cannot be checked in");
        }
    }

    private OffsetDateTime currentTime() {
        return OffsetDateTime.now(clock);
    }
}
