package id.xyz.parkease.domain;

import id.xyz.parkease.domain.ParkingSlot.SlotStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ParkingSlotTest {

    private static final String SLOT_ID = "A-01";
    private static final String OTHER_SLOT_ID = "B-02";
    private static final String VEHICLE_CAR = "CAR";
    private static final String VEHICLE_MOTO = "MOTO";
    private static final int FLOOR_ONE = 1;
    private static final int FLOOR_TWO = 2;
    private static final String LOT_NAME = "Test Lot";
    private static final String TIMEZONE = "Asia/Jakarta";

    @Test
    void buildWithRequiredFields() {
        ParkingSlot slot = ParkingSlot.builder().slotId(SLOT_ID).vehicleType(VEHICLE_CAR).floor(FLOOR_ONE).build();
        assertNotNull(slot.getId());
        assertEquals(SLOT_ID, slot.getSlotId());
        assertEquals(VEHICLE_CAR, slot.getVehicleType());
        assertEquals(Integer.valueOf(FLOOR_ONE), slot.getFloor());
        assertEquals(SlotStatus.AVAILABLE, slot.getStatus());
        assertNotNull(slot.getCreatedAt());
    }

    @Test
    void newSlotHasNoPersistenceVersion() {
        ParkingSlot slot = ParkingSlot.builder().slotId(SLOT_ID).vehicleType(VEHICLE_CAR).floor(FLOOR_ONE).build();
        assertNull(slot.getVersion());
    }

    @Test
    void buildWithLotReference() {
        ParkingLot lot = ParkingLot.builder().name(LOT_NAME).timezone(TIMEZONE).build();
        ParkingSlot slot = ParkingSlot.builder().lot(lot).slotId(SLOT_ID).vehicleType(VEHICLE_CAR).floor(FLOOR_ONE).build();
        assertNotNull(slot.getLot());
        assertEquals(LOT_NAME, slot.getLot().getName());
    }

    @Test
    void generatedIdsAreUnique() {
        UUID first = ParkingSlot.builder().slotId(SLOT_ID).vehicleType(VEHICLE_CAR).floor(FLOOR_ONE).build().getId();
        UUID second = ParkingSlot.builder().slotId(OTHER_SLOT_ID).vehicleType(VEHICLE_MOTO).floor(FLOOR_TWO).build().getId();
        assertNotEquals(first, second);
    }

    @Test
    void markOccupiedChangesStatus() {
        ParkingSlot slot = ParkingSlot.builder().slotId(SLOT_ID).vehicleType(VEHICLE_CAR).floor(FLOOR_ONE).build();
        assertEquals(SlotStatus.OCCUPIED, slot.markOccupied().getStatus());
    }

    @Test
    void markReservedChangesStatus() {
        ParkingSlot slot = ParkingSlot.builder().slotId(SLOT_ID).vehicleType(VEHICLE_CAR).floor(FLOOR_ONE).build();
        assertEquals(SlotStatus.RESERVED, slot.markReserved().getStatus());
    }

    @Test
    void markAvailableChangesStatus() {
        ParkingSlot slot = ParkingSlot.builder().slotId(SLOT_ID).vehicleType(VEHICLE_CAR).floor(FLOOR_ONE).build();
        assertEquals(SlotStatus.AVAILABLE, slot.markOccupied().markAvailable().getStatus());
    }

    @Test
    void applyDefaultsFillsNulls() {
        ParkingLot lot = ParkingLot.builder().name(LOT_NAME).timezone(TIMEZONE).build();
        ParkingSlot slot = new ParkingSlot(null, lot, null, null, null, null, null, null, null);
        slot.applyDefaults();
        assertNotNull(slot.getId());
        assertEquals(SlotStatus.AVAILABLE, slot.getStatus());
        assertNotNull(slot.getCreatedAt());
        assertNotNull(slot.getUpdatedAt());
    }

    @Test
    void applyDefaultsKeepsExistingValues() {
        ParkingLot lot = ParkingLot.builder().name(LOT_NAME).timezone(TIMEZONE).build();
        ParkingSlot slot = ParkingSlot.builder().lot(lot).slotId(SLOT_ID).vehicleType(VEHICLE_CAR).floor(FLOOR_ONE).build();
        UUID id = slot.getId();
        slot.applyDefaults();
        assertEquals(id, slot.getId());
        assertEquals(SlotStatus.AVAILABLE, slot.getStatus());
    }
}
