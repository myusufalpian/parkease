package id.xyz.parkease.mapper;

import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.domain.Reservation;
import id.xyz.parkease.dto.AvailabilityResponse;
import id.xyz.parkease.dto.AvailableSlotResponse;
import id.xyz.parkease.dto.ReservationRequest;
import id.xyz.parkease.dto.ReservationResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReservationMapper {

    public Reservation toReservation(ReservationRequest request, ParkingSlot slot) {
        return Reservation.builder()
                .slot(slot)
                .customerPlate(request.plate())
                .plannedStart(request.plannedStart())
                .plannedEnd(request.plannedEnd())
                .status(Reservation.Status.PENDING)
                .build();
    }

    public ReservationResponse toResponse(Reservation reservation) {
        ParkingSlot slot = reservation.getSlot();
        ReservationResponse.SlotResponse slotResponse = new ReservationResponse.SlotResponse(
                slot.getId(), slot.getSlotId(), slot.getVehicleType(), slot.getFloor());
        return new ReservationResponse(
                reservation.getId(),
                reservation.getStatus(),
                slotResponse,
                reservation.getCustomerPlate(),
                reservation.getPlannedStart(),
                reservation.getPlannedEnd(),
                reservation.getActualStart(),
                reservation.getActualEnd(),
                reservation.getCancellationReason(),
                reservation.isLateCancellation());
    }

    public AvailabilityResponse toAvailability(
            UUID lotId,
            OffsetDateTime plannedStart,
            OffsetDateTime plannedEnd,
            List<ParkingSlot> slots) {
        List<AvailableSlotResponse> responses = slots.stream()
                .map(slot -> new AvailableSlotResponse(slot.getId(), slot.getSlotId(), slot.getVehicleType(), slot.getFloor()))
                .toList();
        return new AvailabilityResponse(lotId, plannedStart, plannedEnd, responses);
    }
}
