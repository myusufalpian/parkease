package id.xyz.parkease.service;

import id.xyz.parkease.domain.ExtensionCharge;
import id.xyz.parkease.domain.OutboxEvent;
import id.xyz.parkease.domain.OutboxEvent.EventType;
import id.xyz.parkease.domain.ParkingLot;
import id.xyz.parkease.domain.RateCard;
import id.xyz.parkease.domain.Reservation;
import id.xyz.parkease.dto.BillingBreakdown;
import id.xyz.parkease.dto.ExtensionChargeResponse;
import id.xyz.parkease.dto.ReservationResponse;
import id.xyz.parkease.exception.ResourceNotFoundException;
import id.xyz.parkease.repository.ExtensionChargeRepository;
import id.xyz.parkease.repository.OutboxEventRepository;
import id.xyz.parkease.repository.ParkingLotRepository;
import id.xyz.parkease.repository.ReservationRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional
public class BillingExtensionService {

    private final ReservationService reservationService;
    private final ReservationRepository reservationRepository;
    private final ParkingLotRepository parkingLotRepository;
    private final RateCardResolver rateCardResolver;
    private final BillingCalculator billingCalculator;
    private final ExtensionChargeRepository extensionChargeRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final Clock clock;

    public BillingExtensionService(
            ReservationService reservationService,
            ReservationRepository reservationRepository,
            ParkingLotRepository parkingLotRepository,
            RateCardResolver rateCardResolver,
            BillingCalculator billingCalculator,
            ExtensionChargeRepository extensionChargeRepository,
            OutboxEventRepository outboxEventRepository,
            Clock clock) {
        this.reservationService = Objects.requireNonNull(reservationService);
        this.reservationRepository = Objects.requireNonNull(reservationRepository);
        this.parkingLotRepository = Objects.requireNonNull(parkingLotRepository);
        this.rateCardResolver = Objects.requireNonNull(rateCardResolver);
        this.billingCalculator = Objects.requireNonNull(billingCalculator);
        this.extensionChargeRepository = Objects.requireNonNull(extensionChargeRepository);
        this.outboxEventRepository = Objects.requireNonNull(outboxEventRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public ExtensionChargeResponse extend(UUID reservationId, OffsetDateTime newPlannedEnd) {
        return extend(reservationId, newPlannedEnd, currentTime());
    }

    public ExtensionChargeResponse extend(UUID reservationId, OffsetDateTime newPlannedEnd, OffsetDateTime requestedAt) {
        Reservation before = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("reservation was not found"));
        OffsetDateTime previousPlannedEnd = before.getPlannedEnd();
        ParkingLot lot = before.getSlot().getLot();
        String vehicleType = before.getSlot().getVehicleType();

        ReservationResponse reservationResponse = reservationService.extend(reservationId, newPlannedEnd, requestedAt);

        ZoneId lotTimezone = ZoneId.of(lot.getTimezone());
        RateCard rateCard = rateCardResolver.resolve(lot.getId(), vehicleType, requestedAt);
        BillingBreakdown additionalBreakdown = billingCalculator.calculate(
                previousPlannedEnd, newPlannedEnd, lotTimezone, rateCard);

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("reservation was not found"));
        ExtensionCharge charge = ExtensionCharge.builder()
                .reservation(reservation)
                .additionalDurationMinutes(additionalBreakdown.durationMinutes())
                .amount(additionalBreakdown.total())
                .currency(additionalBreakdown.currency())
                .build();
        ExtensionCharge savedCharge = extensionChargeRepository.saveAndFlush(charge);

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .eventType(EventType.EXTENSION_PAYMENT)
                .aggregateId(savedCharge.getId())
                .payloadJson("{}")
                .build();
        outboxEventRepository.save(outboxEvent);

        return new ExtensionChargeResponse(
                reservationResponse, savedCharge.getAdditionalDurationMinutes(), savedCharge.getAmount(), savedCharge.getCurrency());
    }

    private OffsetDateTime currentTime() {
        return OffsetDateTime.now(clock);
    }
}
