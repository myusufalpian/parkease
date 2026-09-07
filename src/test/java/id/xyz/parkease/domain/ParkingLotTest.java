package id.xyz.parkease.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ParkingLotTest {

    private static final String LOT_NAME = "Test Lot A";
    private static final String LOCATION = "Jakarta Selatan";
    private static final String TIMEZONE = "Asia/Jakarta";

    @Test
    void buildWithNameAndTimezone() {
        ParkingLot lot = ParkingLot.builder().name(LOT_NAME).timezone(TIMEZONE).build();
        assertNotNull(lot.getId());
        assertEquals(LOT_NAME, lot.getName());
        assertEquals(TIMEZONE, lot.getTimezone());
        assertNull(lot.getLocation());
        assertNotNull(lot.getCreatedAt());
        assertNotNull(lot.getUpdatedAt());
    }

    @Test
    void buildWithLocation() {
        ParkingLot lot = ParkingLot.builder().name(LOT_NAME).location(LOCATION).timezone(TIMEZONE).build();
        assertEquals(LOCATION, lot.getLocation());
    }

    @Test
    void generatedIdsAreUnique() {
        UUID first = ParkingLot.builder().name(LOT_NAME).timezone(TIMEZONE).build().getId();
        UUID second = ParkingLot.builder().name(LOT_NAME).timezone(TIMEZONE).build().getId();
        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first, second);
    }

    @Test
    void applyDefaultsFillsNulls() {
        ParkingLot lot = new ParkingLot(null, null, null, null, null, null, null);
        lot.applyDefaults();
        assertNotNull(lot.getId());
        assertNotNull(lot.getCreatedAt());
        assertNotNull(lot.getUpdatedAt());
    }

    @Test
    void applyDefaultsKeepsExistingValues() {
        ParkingLot lot = ParkingLot.builder().name(LOT_NAME).timezone(TIMEZONE).build();
        UUID id = lot.getId();
        lot.applyDefaults();
        assertEquals(id, lot.getId());
    }

    @Test
    void timestampsAreInitialized() {
        OffsetDateTime before = OffsetDateTime.now().minusSeconds(5);
        ParkingLot lot = ParkingLot.builder().name(LOT_NAME).timezone(TIMEZONE).build();
        assertNotNull(lot.getCreatedAt());
        assertNotNull(lot.getUpdatedAt());
        assertFalse(lot.getCreatedAt().isBefore(before));
    }
}
