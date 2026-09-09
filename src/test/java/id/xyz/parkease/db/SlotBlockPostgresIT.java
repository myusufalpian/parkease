package id.xyz.parkease.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("postgres")
class SlotBlockPostgresIT {

    private static final String POSTGRES_IMAGE = "postgres:16-alpine";
    private static final String EXCLUSION_SQL_STATE = "23P01";

    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void startContainer() {
        PostgresIntegrationSupport.requireDockerOrSkip();
        postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE);
        postgres.start();
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) postgres.stop();
    }

    @BeforeEach
    void migrateFreshSchema() {
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private UUID insertLot(Connection c) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO parking_lot (id, name, timezone) VALUES (?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setString(2, "Lot IT");
            ps.setString(3, "Asia/Jakarta");
            assertEquals(1, ps.executeUpdate());
        }
        return id;
    }

    private UUID insertSlot(Connection c, UUID lotId) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO parking_slot (id, lot_id, slot_id, vehicle_type, floor) VALUES (?, ?, ?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, lotId);
            ps.setString(3, "A-01");
            ps.setString(4, "CAR");
            ps.setInt(5, 1);
            assertEquals(1, ps.executeUpdate());
        }
        return id;
    }

    private void insertBlock(Connection c, UUID slotId, String start, String end, String status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO parking_slot_block (id, slot_id, blocked_start, blocked_end, reason, status) VALUES (?, ?, ?::timestamptz, ?::timestamptz, ?, ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, slotId);
            ps.setString(3, start);
            ps.setString(4, end);
            ps.setString(5, "maintenance");
            ps.setString(6, status);
            assertEquals(1, ps.executeUpdate());
        }
    }

    @Test
    void overlappingActiveBlocksAreRejected() throws Exception {
        try (Connection c = connection()) {
            UUID lotId = insertLot(c);
            UUID slotId = insertSlot(c, lotId);
            insertBlock(c, slotId, "2024-01-15T09:00:00+07:00", "2024-01-15T11:00:00+07:00", "ACTIVE");
            SQLException ex = assertThrows(SQLException.class, () -> insertBlock(c, slotId, "2024-01-15T10:00:00+07:00", "2024-01-15T12:00:00+07:00", "ACTIVE"));
            assertEquals(EXCLUSION_SQL_STATE, ex.getSQLState());
        }
    }

    @Test
    void touchingBlockEndpointsAreAllowed() throws Exception {
        try (Connection c = connection()) {
            UUID lotId = insertLot(c);
            UUID slotId = insertSlot(c, lotId);
            insertBlock(c, slotId, "2024-01-15T09:00:00+07:00", "2024-01-15T11:00:00+07:00", "ACTIVE");
            insertBlock(c, slotId, "2024-01-15T11:00:00+07:00", "2024-01-15T13:00:00+07:00", "ACTIVE");
        }
    }

    @Test
    void cancelledBlockDoesNotBlockNewActiveBlock() throws Exception {
        try (Connection c = connection()) {
            UUID lotId = insertLot(c);
            UUID slotId = insertSlot(c, lotId);
            insertBlock(c, slotId, "2024-01-15T09:00:00+07:00", "2024-01-15T11:00:00+07:00", "CANCELLED");
            insertBlock(c, slotId, "2024-01-15T10:00:00+07:00", "2024-01-15T12:00:00+07:00", "ACTIVE");
        }
    }

    @Test
    void expiredBlockDoesNotBlockNewActiveBlock() throws Exception {
        try (Connection c = connection()) {
            UUID lotId = insertLot(c);
            UUID slotId = insertSlot(c, lotId);
            insertBlock(c, slotId, "2024-01-15T09:00:00+07:00", "2024-01-15T11:00:00+07:00", "EXPIRED");
            insertBlock(c, slotId, "2024-01-15T10:00:00+07:00", "2024-01-15T12:00:00+07:00", "ACTIVE");
        }
    }
}
