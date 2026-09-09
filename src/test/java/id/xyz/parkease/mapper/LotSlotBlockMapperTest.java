package id.xyz.parkease.mapper;

import id.xyz.parkease.domain.ParkingLot;
import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.domain.ParkingSlotBlock;
import id.xyz.parkease.dto.BlockResponse;
import id.xyz.parkease.dto.LotResponse;
import id.xyz.parkease.dto.SlotResponse;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LotSlotBlockMapperTest {

    @Test
    void lotMapperMapsAllFields() {
        ParkingLot lot = ParkingLot.builder().name("Central").location("Loc").timezone("Asia/Jakarta").build();
        LotMapper mapper = new LotMapper();
        LotResponse response = mapper.toResponse(lot);
        assertEquals("Central", response.name());
        assertEquals("Asia/Jakarta", response.timezone());
    }

    @Test
    void slotMapperMapsAllFields() {
        ParkingSlot slot = ParkingSlot.builder().slotId("A-01").vehicleType("CAR").floor(2).status(ParkingSlot.SlotStatus.AVAILABLE).build();
        SlotMapper mapper = new SlotMapper();
        SlotResponse response = mapper.toResponse(slot);
        assertEquals("A-01", response.slotId());
        assertEquals("AVAILABLE", response.status());
    }

    @Test
    void blockMapperMapsAllFields() {
        ParkingSlot slot = ParkingSlot.builder().slotId("A-01").vehicleType("CAR").floor(1).build();
        ParkingSlotBlock block = ParkingSlotBlock.builder().slot(slot).blockedStart(OffsetDateTime.parse("2024-01-15T09:00:00Z"))
                .blockedEnd(OffsetDateTime.parse("2024-01-15T11:00:00Z")).reason("cleaning").status(ParkingSlotBlock.BlockStatus.ACTIVE).build();
        BlockMapper mapper = new BlockMapper();
        BlockResponse response = mapper.toResponse(block);
        assertEquals("cleaning", response.reason());
        assertEquals("ACTIVE", response.status());
    }
}
