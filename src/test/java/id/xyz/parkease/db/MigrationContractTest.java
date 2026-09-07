package id.xyz.parkease.db;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationContractTest {

    private static final String V1_PATH = "/db/migration/V1__initial_schema.sql";
    private static final String V2_PATH = "/db/migration/V2__add_exclusion_constraint.sql";
    private static final String V3_PATH = "/db/migration/V3__add_parking_slot_version.sql";
    private static final String HALF_OPEN = "'[)'";
    private static final String SLOT_EXCLUDE = "slot_id WITH =";
    private static final String UPDATED_AT = "updated_at";
    private static final String SLOT_VERSION = "version BIGINT NOT NULL DEFAULT 0";

    private String load(String path) throws Exception {
        try (java.io.InputStream input = getClass().getResourceAsStream(path)) {
            assertTrue(input != null, "missing migration " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void v1CoversMutableTimestamps() throws Exception {
        assertTrue(load(V1_PATH).contains(UPDATED_AT));
    }

    @Test
    void v2UsesHalfOpenSlotRange() throws Exception {
        String v2 = load(V2_PATH);
        assertTrue(v2.contains(HALF_OPEN));
        assertTrue(v2.contains(SLOT_EXCLUDE));
    }

    @Test
    void v2UsesPostgresZonedTimestampRange() throws Exception {
        assertTrue(load(V2_PATH).contains("tstzrange"));
    }

    @Test
    void v2IgnoresTerminalReservationHistory() throws Exception {
        String v2 = load(V2_PATH);
        assertTrue(v2.contains("PENDING"));
        assertTrue(v2.contains("ACTIVE"));
    }

    @Test
    void v3AddsParkingSlotVersion() throws Exception {
        assertTrue(load(V3_PATH).contains(SLOT_VERSION));
    }
}
