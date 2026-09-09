package id.xyz.parkease.service;

import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.domain.ParkingSlotBlock.BlockStatus;
import id.xyz.parkease.domain.Reservation.Status;
import id.xyz.parkease.exception.ConflictException;
import id.xyz.parkease.exception.ResourceNotFoundException;
import id.xyz.parkease.repository.ParkingSlotBlockRepository;
import id.xyz.parkease.repository.ParkingSlotRepository;
import id.xyz.parkease.repository.ReservationRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryConflictService {

    private final ParkingSlotRepository parkingSlotRepository;
    private final ReservationRepository reservationRepository;
    private final ParkingSlotBlockRepository blockRepository;

    public InventoryConflictService(
            ParkingSlotRepository parkingSlotRepository,
            ReservationRepository reservationRepository,
            ParkingSlotBlockRepository blockRepository) {
        this.parkingSlotRepository = Objects.requireNonNull(parkingSlotRepository);
        this.reservationRepository = Objects.requireNonNull(reservationRepository);
        this.blockRepository = Objects.requireNonNull(blockRepository);
    }

    public ParkingSlot lockSlot(UUID slotId) {
        return parkingSlotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("parking slot was not found"));
    }

    public boolean hasOverlappingReservation(UUID slotId, OffsetDateTime start, OffsetDateTime end, UUID excludedReservationId) {
        List<id.xyz.parkease.domain.Reservation> active = Stream.of(
                        reservationRepository.findBySlot_IdAndStatus(slotId, Status.PENDING),
                        reservationRepository.findBySlot_IdAndStatus(slotId, Status.ACTIVE))
                .flatMap(List::stream)
                .filter(r -> !r.getId().equals(excludedReservationId))
                .filter(r -> start.isBefore(r.getPlannedEnd()) && r.getPlannedStart().isBefore(end))
                .toList();
        return !active.isEmpty();
    }

    public boolean hasActiveBlockOverlap(UUID slotId, OffsetDateTime start, OffsetDateTime end) {
        return !blockRepository.findActiveOverlapping(slotId, BlockStatus.ACTIVE, start, end).isEmpty();
    }

    @Transactional
    public ParkingSlot requireNoConflictForReservation(UUID slotId, OffsetDateTime start, OffsetDateTime end, UUID excludedReservationId) {
        ParkingSlot locked = lockSlot(slotId);
        if (hasOverlappingReservation(slotId, start, end, excludedReservationId)) {
            throw new ConflictException("the requested parking window is no longer available");
        }
        if (hasActiveBlockOverlap(slotId, start, end)) {
            throw new ConflictException("parking slot is blocked for the requested time window");
        }
        return locked;
    }

    @Transactional
    public ParkingSlot requireNoConflictForBlock(UUID slotId, OffsetDateTime start, OffsetDateTime end) {
        ParkingSlot locked = lockSlot(slotId);
        if (hasOverlappingReservation(slotId, start, end, null)) {
            throw new ConflictException("parking slot has a conflicting reservation for the requested block window");
        }
        if (hasActiveBlockOverlap(slotId, start, end)) {
            throw new ConflictException("parking slot is already blocked for the requested time window");
        }
        return locked;
    }

    public boolean isSlotAvailableForWindow(ParkingSlot slot, OffsetDateTime start, OffsetDateTime end) {
        if (slot.getStatus() == ParkingSlot.SlotStatus.MAINTENANCE) {
            return false;
        }
        if (hasOverlappingReservation(slot.getId(), start, end, null)) {
            return false;
        }
      return !hasActiveBlockOverlap(slot.getId(), start, end);
    }
}
