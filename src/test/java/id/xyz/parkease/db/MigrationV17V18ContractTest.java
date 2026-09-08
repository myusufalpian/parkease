package id.xyz.parkease.db;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationV17V18ContractTest {

    private static final String V17_PATH = "/db/migration/V17__add_parking_slot_block.sql";
    private static final String V18_PATH = "/db/migration/V18__seed_canonical_inventory.sql";

    private String load(String path) throws Exception {
        try (java.io.InputStream input = getClass().getResourceAsStream(path)) {
            org.junit.jupiter.api.Assertions.assertNotNull(input, "missing migration " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void v17CreatesBlockTableWithHalfOpenConstraint() throws Exception {
        String v17 = load(V17_PATH);
        assertTrue(v17.contains("parking_slot_block"));
        assertTrue(v17.contains("blocked_start"));
        assertTrue(v17.contains("blocked_end"));
        assertTrue(v17.contains("tstzrange"));
        assertTrue(v17.contains("'[)'"));
        assertTrue(v17.contains("EXCLUDE USING GIST"));
        assertTrue(v17.contains("slot_id WITH ="));
        assertTrue(v17.contains("status = 'ACTIVE'"));
        assertTrue(v17.contains("blocked_start < blocked_end"));
    }

    @Test
    void v18SeedsCanonicalLotsAndSlotsWithStableUuids() throws Exception {
        String v18 = load(V18_PATH);
        assertTrue(v18.contains("11111111-1111-1111-1111-111111111111"));
        assertTrue(v18.contains("22222222-2222-2222-2222-222222222222"));
        assertTrue(v18.contains("parking_lot"));
        assertTrue(v18.contains("parking_slot"));
        assertTrue(v18.contains("rate_card"));
        assertTrue(v18.contains("ParkEase Central"));
    }
}
