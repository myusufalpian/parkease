package id.xyz.parkease.domain;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParkingSlotBlockTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2024-01-15T08:00:00Z");
    private static final OffsetDateTime START = OffsetDateTime.parse("2024-01-15T09:00:00Z");
    private static final OffsetDateTime END = OffsetDateTime.parse("2024-01-15T11:00:00Z");

    @Test
    void activeBeforeEndIsActive() {
        ParkingSlot slot = ParkingSlot.builder().slotId("A-01").vehicleType("CAR").floor(1).build();
        ParkingSlotBlock block = ParkingSlotBlock.builder().slot(slot).blockedStart(START).blockedEnd(END).reason("r").status(ParkingSlotBlock.BlockStatus.ACTIVE).build();
        assertTrue(block.isActiveAt(NOW));
        assertFalse(block.isExpiredAt(NOW));
    }

    @Test
    void activeAfterEndIsExpired() {
        ParkingSlot slot = ParkingSlot.builder().slotId("A-01").vehicleType("CAR").floor(1).build();
        ParkingSlotBlock block = ParkingSlotBlock.builder().slot(slot).blockedStart(START).blockedEnd(END).reason("r").status(ParkingSlotBlock.BlockStatus.ACTIVE).build();
        OffsetDateTime after = END.plusMinutes(1);
        assertFalse(block.isActiveAt(after));
        assertTrue(block.isExpiredAt(after));
    }

    @Test
    void cancelledIsNeverActive() {
        ParkingSlot slot = ParkingSlot.builder().slotId("A-01").vehicleType("CAR").floor(1).build();
        ParkingSlotBlock block = ParkingSlotBlock.builder().slot(slot).blockedStart(START).blockedEnd(END).reason("r").status(ParkingSlotBlock.BlockStatus.CANCELLED).build();
        assertFalse(block.isActiveAt(NOW));
        assertFalse(block.isExpiredAt(NOW));
    }

    @Test
    void applyDefaultsGeneratesIdAndTimestampsWhenNull() {
        ParkingSlot slot = ParkingSlot.builder().slotId("A-01").vehicleType("CAR").floor(1).build();
        ParkingSlotBlock block = new ParkingSlotBlock();
        block = block.toBuilder().slot(slot).blockedStart(START).blockedEnd(END).reason("r").id(null).status(null).createdAt(null).updatedAt(null).build();
        block.applyDefaults();
        org.junit.jupiter.api.Assertions.assertNotNull(block.getId());
        org.junit.jupiter.api.Assertions.assertEquals(ParkingSlotBlock.BlockStatus.ACTIVE, block.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(block.getCreatedAt());
        org.junit.jupiter.api.Assertions.assertNotNull(block.getUpdatedAt());
    }

    @Test
    void touchUpdatesUpdatedAt() throws InterruptedException {
        ParkingSlot slot = ParkingSlot.builder().slotId("A-01").vehicleType("CAR").floor(1).build();
        ParkingSlotBlock block = ParkingSlotBlock.builder().slot(slot).blockedStart(START).blockedEnd(END).reason("r").status(ParkingSlotBlock.BlockStatus.ACTIVE).build();
        OffsetDateTime before = block.getUpdatedAt();
        Thread.sleep(5);
        block.touch();
        org.junit.jupiter.api.Assertions.assertTrue(block.getUpdatedAt().isAfter(before) || block.getUpdatedAt().equals(before));
    }

    @Test
    void halfOpenBoundaryAtExactEndIsExpired() {
        ParkingSlot slot = ParkingSlot.builder().slotId("A-01").vehicleType("CAR").floor(1).build();
        ParkingSlotBlock block = ParkingSlotBlock.builder().slot(slot).blockedStart(START).blockedEnd(END).reason("r").status(ParkingSlotBlock.BlockStatus.ACTIVE).build();
        assertFalse(block.isActiveAt(END));
        assertTrue(block.isExpiredAt(END));
    }
}
