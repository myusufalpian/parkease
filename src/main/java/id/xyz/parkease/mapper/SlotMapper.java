package id.xyz.parkease.mapper;

import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.dto.SlotResponse;
import org.springframework.stereotype.Component;

@Component
public class SlotMapper {

    public SlotResponse toResponse(ParkingSlot slot) {
        return new SlotResponse(slot.getId(), slot.getSlotId(), slot.getVehicleType(), slot.getFloor(), slot.getStatus().name());
    }
}
