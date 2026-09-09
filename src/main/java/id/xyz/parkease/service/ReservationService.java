package id.xyz.parkease.service;

import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.domain.ParkingSlot.SlotStatus;
import id.xyz.parkease.domain.Reservation;
import id.xyz.parkease.domain.Reservation.Status;
import id.xyz.parkease.dto.AvailabilityResponse;
import id.xyz.parkease.dto.ReservationRequest;
import id.xyz.parkease.dto.ReservationResponse;
import id.xyz.parkease.event.ReservationCheckedOutEvent;
import id.xyz.parkease.exception.BusinessValidationException;
import id.xyz.parkease.exception.ConflictException;
import id.xyz.parkease.exception.ResourceNotFoundException;
import id.xyz.parkease.exception.SqlState;
import id.xyz.parkease.mapper.ReservationMapper;
import id.xyz.parkease.repository.ParkingLotRepository;
import id.xyz.parkease.repository.ParkingSlotRepository;
import id.xyz.parkease.repository.ReservationRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReservationService {

    public static final long CHECK_IN_GRACE_MINUTES = 30L;
    private static final int CANCELLATION_REASON_MAX_LENGTH = 100;
    private static final long MAX_RESERVATION_DAYS = 31L;

    private final ParkingLotRepository parkingLotRepository;
    private final ParkingSlotRepository parkingSlotRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationMapper reservationMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final BookingWindowValidator bookingWindowValidator;
    private final InventoryConflictService inventoryConflictService;
    private final Clock clock;

    public ReservationService(
            ParkingLotRepository parkingLotRepository,
            ParkingSlotRepository parkingSlotRepository,
            ReservationRepository reservationRepository,
            ReservationMapper reservationMapper,
            ApplicationEventPublisher eventPublisher,
            BookingWindowValidator bookingWindowValidator,
            InventoryConflictService inventoryConflictService,
            Clock clock) {
        this.parkingLotRepository = Objects.requireNonNull(parkingLotRepository);
        this.parkingSlotRepository = Objects.requireNonNull(parkingSlotRepository);
        this.reservationRepository = Objects.requireNonNull(reservationRepository);
        this.reservationMapper = Objects.requireNonNull(reservationMapper);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.bookingWindowValidator = Objects.requireNonNull(bookingWindowValidator);
        this.inventoryConflictService = Objects.requireNonNull(inventoryConflictService);
        this.clock = Objects.requireNonNull(clock);
    }

    public ReservationResponse createReservation(ReservationRequest request) {
        return createReservation(request, currentTime());
    }

    public ReservationResponse createReservation(ReservationRequest request, OffsetDateTime requestedAt) {
        validateRequest(request);
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");
        bookingWindowValidator.validate(request.plannedStart(), request.plannedEnd(), requestedAt);
        parkingLotRepository.findById(request.lotId())
                .orElseThrow(() -> new ResourceNotFoundException("parking lot was not found"));

        ParkingSlot slot = findDeterministicAvailableSlot(request.lotId(), request.vehicleType(), request.plannedStart(), request.plannedEnd());
        ParkingSlot locked = inventoryConflictService.requireNoConflictForReservation(slot.getId(), request.plannedStart(), request.plannedEnd(), null);
        ParkingSlot reservedSlot = parkingSlotRepository.save(locked.markReserved());
        Reservation reservation = reservationMapper.toReservation(request, reservedSlot);
        try {
            Reservation savedReservation = reservationRepository.saveAndFlush(reservation);
            return reservationMapper.toResponse(savedReservation);
        } catch (DataIntegrityViolationException exception) {
            if (SqlState.isOverlapOrDeadlock(exception)) {
                throw new ConflictException("the requested parking window is no longer available");
            }
            throw exception;
        }
    }

    public ReservationResponse checkIn(UUID reservationId) {
        return checkIn(reservationId, currentTime());
    }

    public ReservationResponse checkIn(UUID reservationId, OffsetDateTime requestedAt) {
        Reservation reservation = getReservation(reservationId);
        requireTime(requestedAt);
        if (reservation.getStatus() != Status.PENDING) {
            throw new ConflictException("reservation is not pending");
        }

        OffsetDateTime latestCheckIn = reservation.getPlannedStart().plusMinutes(CHECK_IN_GRACE_MINUTES);
        OffsetDateTime actualStart = requestedAt.isAfter(latestCheckIn) ? latestCheckIn : requestedAt;
        ParkingSlot slot = inventoryConflictService.lockSlot(reservation.getSlot().getId());
        if (requestedAt.isAfter(latestCheckIn)) {
            Reservation noShow = reservationRepository.save(reservation.toBuilder().slot(slot).build().markNoShow(actualStart));
            parkingSlotRepository.save(slot.markAvailable());
            return reservationMapper.toResponse(noShow);
        }

        ParkingSlot occupiedSlot = parkingSlotRepository.save(slot.markOccupied());
        Reservation activeReservation = reservation.toBuilder().slot(occupiedSlot).build().checkIn(actualStart);
        Reservation savedReservation = reservationRepository.save(activeReservation);
        return reservationMapper.toResponse(savedReservation);
    }

    public ReservationResponse checkOut(UUID reservationId) {
        return checkOut(reservationId, currentTime());
    }

    public ReservationResponse checkOut(UUID reservationId, OffsetDateTime requestedAt) {
        Reservation reservation = getReservation(reservationId);
        requireTime(requestedAt);
        if (reservation.getStatus() == Status.COMPLETED) {
            return reservationMapper.toResponse(reservation);
        }
        if (reservation.getStatus() != Status.ACTIVE) {
            throw new ConflictException("reservation is not active");
        }
        if (reservation.getActualStart() != null && requestedAt.isBefore(reservation.getActualStart())) {
            throw new BusinessValidationException("checkout time must not be before check-in time");
        }

        ParkingSlot lockedSlot = inventoryConflictService.lockSlot(reservation.getSlot().getId());
        ParkingSlot availableSlot = parkingSlotRepository.save(lockedSlot.markAvailable());
        Reservation completedReservation = reservation.toBuilder().slot(availableSlot).build().complete(requestedAt);
        Reservation savedReservation = reservationRepository.save(completedReservation);
        eventPublisher.publishEvent(new ReservationCheckedOutEvent(savedReservation.getId(), requestedAt));
        return reservationMapper.toResponse(savedReservation);
    }

    public ReservationResponse cancel(UUID reservationId, String reason) {
        return cancel(reservationId, reason, currentTime());
    }

    public ReservationResponse cancel(UUID reservationId, String reason, OffsetDateTime requestedAt) {
        Reservation reservation = getReservation(reservationId);
        requireTime(requestedAt);
        validateCancellationReason(reason);
        if (isTerminal(reservation.getStatus())) {
            return reservationMapper.toResponse(reservation);
        }

        boolean lateCancellation = false;
        if (reservation.getStatus() == Status.ACTIVE) {
            OffsetDateTime latestLateCancellation = reservation.getPlannedStart().plusMinutes(CHECK_IN_GRACE_MINUTES);
            if (requestedAt.isAfter(latestLateCancellation)) {
                throw new ConflictException("active reservation can no longer be cancelled");
            }
            lateCancellation = true;
        } else if (reservation.getStatus() != Status.PENDING) {
            throw new ConflictException("reservation cannot be cancelled");
        }

        ParkingSlot lockedSlot = inventoryConflictService.lockSlot(reservation.getSlot().getId());
        ParkingSlot availableSlot = parkingSlotRepository.save(lockedSlot.markAvailable());
        Reservation cancelled = reservation.toBuilder().slot(availableSlot).build().cancel(reason, lateCancellation);
        Reservation savedReservation = reservationRepository.save(cancelled);
        return reservationMapper.toResponse(savedReservation);
    }

    public ReservationResponse extend(UUID reservationId, OffsetDateTime plannedEnd) {
        return extend(reservationId, plannedEnd, currentTime());
    }

    public ReservationResponse extend(UUID reservationId, OffsetDateTime plannedEnd, OffsetDateTime requestedAt) {
        Reservation reservation = getReservation(reservationId);
        requireTime(requestedAt);
        bookingWindowValidator.validate(reservation.getPlannedStart(), plannedEnd, requestedAt);
        if (reservation.getStatus() != Status.PENDING && reservation.getStatus() != Status.ACTIVE) {
            throw new ConflictException("terminal reservation cannot be extended");
        }
        if (!plannedEnd.isAfter(reservation.getPlannedEnd())) {
            throw new BusinessValidationException("extension must be later than the current planned end");
        }
        inventoryConflictService.lockSlot(reservation.getSlot().getId());
        if (inventoryConflictService.hasOverlappingReservation(reservation.getSlot().getId(), reservation.getPlannedStart(), plannedEnd, reservation.getId())) {
            throw new ConflictException("the requested extension conflicts with another reservation");
        }
        if (inventoryConflictService.hasActiveBlockOverlap(reservation.getSlot().getId(), reservation.getPlannedStart(), plannedEnd)) {
            throw new ConflictException("the requested extension conflicts with a slot block");
        }

        Reservation extended = reservation.extendTo(plannedEnd);
        try {
            Reservation savedReservation = reservationRepository.saveAndFlush(extended);
            return reservationMapper.toResponse(savedReservation);
        } catch (DataIntegrityViolationException exception) {
            if (SqlState.isOverlapOrDeadlock(exception)) {
                throw new ConflictException("the requested extension conflicts with another reservation");
            }
            throw exception;
        }
    }

    private static final List<Status> ACTIVE_STATUSES = List.of(Status.PENDING, Status.ACTIVE);
    private static final SlotStatus MAINTENANCE_STATUS = SlotStatus.MAINTENANCE;
    private static final id.xyz.parkease.domain.ParkingSlotBlock.BlockStatus ACTIVE_BLOCK_STATUS =
            id.xyz.parkease.domain.ParkingSlotBlock.BlockStatus.ACTIVE;

    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(
            UUID lotId,
            OffsetDateTime plannedStart,
            OffsetDateTime plannedEnd,
            String vehicleType) {
        validateAvailabilityWindow(plannedStart, plannedEnd);
        parkingLotRepository.findById(lotId)
                .orElseThrow(() -> new ResourceNotFoundException("parking lot was not found"));
        List<ParkingSlot> availableSlots = parkingSlotRepository.findAvailableSlots(
                lotId, vehicleType, plannedStart, plannedEnd, ACTIVE_STATUSES, MAINTENANCE_STATUS, ACTIVE_BLOCK_STATUS);
        return reservationMapper.toAvailability(lotId, plannedStart, plannedEnd, availableSlots);
    }

    private ParkingSlot findDeterministicAvailableSlot(UUID lotId, String vehicleType, OffsetDateTime plannedStart, OffsetDateTime plannedEnd) {
        return parkingSlotRepository.findAvailableSlots(
                        lotId, vehicleType, plannedStart, plannedEnd, ACTIVE_STATUSES, MAINTENANCE_STATUS, ACTIVE_BLOCK_STATUS)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ConflictException("no parking slot is available for the requested vehicle type"));
    }

    private Reservation getReservation(UUID reservationId) {
        Objects.requireNonNull(reservationId, "reservationId must not be null");
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("reservation was not found"));
    }

    private boolean isTerminal(Status status) {
        return status == Status.CANCELLED || status == Status.COMPLETED || status == Status.NO_SHOW;
    }

    private void validateRequest(ReservationRequest request) {
        if (request == null) {
            throw new BusinessValidationException("reservation request is required");
        }
    }

    private void validateAvailabilityWindow(OffsetDateTime plannedStart, OffsetDateTime plannedEnd) {
        if (plannedStart == null || plannedEnd == null || !plannedStart.isBefore(plannedEnd)) {
            throw new BusinessValidationException("planned end must be after planned start");
        }
        if (plannedStart.plusDays(MAX_RESERVATION_DAYS).isBefore(plannedEnd)) {
            throw new BusinessValidationException("planned window exceeds the maximum allowed duration");
        }
    }

    private void validateCancellationReason(String reason) {
        if (reason != null && reason.length() > CANCELLATION_REASON_MAX_LENGTH) {
            throw new BusinessValidationException("cancellation reason is too long");
        }
    }

    private void requireTime(OffsetDateTime requestedAt) {
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");
    }

    private OffsetDateTime currentTime() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
