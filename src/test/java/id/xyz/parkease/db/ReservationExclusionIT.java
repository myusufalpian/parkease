package id.xyz.parkease.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("postgres")
class ReservationExclusionIT {

    private static final String POSTGRES_IMAGE = "postgres:16-alpine";
    private static final String EXCLUSION_SQL_STATE = "23P01";
    private static final String LOT_NAME = "Lot IT";
    private static final String TIMEZONE = "Asia/Jakarta";
    private static final String SLOT_ID = "A-01";
    private static final String OTHER_SLOT_ID = "A-02";
    private static final String VEHICLE = "CAR";
    private static final int FLOOR = 1;
    private static final int CONCURRENT_ATTEMPTS = 20;
    private static final long TERMINATION_TIMEOUT_SECONDS = 30;
    private static final String CONCURRENT_START = "2024-01-15T09:00:00+07:00";
    private static final String CONCURRENT_END = "2024-01-15T11:00:00+07:00";

    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void startContainer() {
        PostgresIntegrationSupport.requireDockerOrSkip();
        postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE);
        postgres.start();
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
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

    private UUID insertLot(Connection connection) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO parking_lot (id, name, timezone) VALUES (?, ?, ?)")) {
            statement.setObject(1, id);
            statement.setString(2, LOT_NAME);
            statement.setString(3, TIMEZONE);
            assertEquals(1, statement.executeUpdate());
        }
        return id;
    }

    private UUID insertSlot(Connection connection, UUID lotId, String slotId) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO parking_slot (id, lot_id, slot_id, vehicle_type, floor) VALUES (?, ?, ?, ?, ?)")) {
            statement.setObject(1, id);
            statement.setObject(2, lotId);
            statement.setString(3, slotId);
            statement.setString(4, VEHICLE);
            statement.setInt(5, FLOOR);
            assertEquals(1, statement.executeUpdate());
        }
        return id;
    }

    private void insertReservation(Connection connection, UUID slotId, String start, String end, String status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO reservation (id, slot_id, planned_start, planned_end, status) VALUES (?, ?, ?::timestamptz, ?::timestamptz, ?)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, slotId);
            statement.setString(3, start);
            statement.setString(4, end);
            statement.setString(5, status);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private String concurrentInsert(UUID slotId, CyclicBarrier barrier) throws Exception {
        try (Connection connection = connection()) {
            barrier.await(TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            try {
                insertReservation(connection, slotId, CONCURRENT_START, CONCURRENT_END, "PENDING");
                return null;
            } catch (SQLException exception) {
                return exception.getSQLState();
            }
        }
    }

    @Test
    void overlappingPendingReservationsAreRejected() throws Exception {
        try (Connection connection = connection()) {
            UUID lotId = insertLot(connection);
            UUID slotId = insertSlot(connection, lotId, SLOT_ID);
            insertReservation(connection, slotId, "2024-01-15T09:00:00+07:00", "2024-01-15T11:00:00+07:00", "PENDING");
            SQLException failure = assertThrows(
                    SQLException.class,
                    () -> insertReservation(connection, slotId, "2024-01-15T10:00:00+07:00", "2024-01-15T12:00:00+07:00", "PENDING"));
            assertEquals(EXCLUSION_SQL_STATE, failure.getSQLState());
        }
    }

    @Test
    void touchingEndpointsAreAllowed() throws Exception {
        try (Connection connection = connection()) {
            UUID lotId = insertLot(connection);
            UUID slotId = insertSlot(connection, lotId, SLOT_ID);
            insertReservation(connection, slotId, "2024-01-15T09:00:00+07:00", "2024-01-15T11:00:00+07:00", "PENDING");
            insertReservation(connection, slotId, "2024-01-15T11:00:00+07:00", "2024-01-15T13:00:00+07:00", "PENDING");
        }
    }

    @Test
    void cancelledHistoryDoesNotBlockNewBooking() throws Exception {
        try (Connection connection = connection()) {
            UUID lotId = insertLot(connection);
            UUID slotId = insertSlot(connection, lotId, SLOT_ID);
            insertReservation(connection, slotId, "2024-01-15T09:00:00+07:00", "2024-01-15T11:00:00+07:00", "CANCELLED");
            insertReservation(connection, slotId, "2024-01-15T10:00:00+07:00", "2024-01-15T12:00:00+07:00", "PENDING");
        }
    }

    @Test
    void overlapOnDifferentSlotIsAllowed() throws Exception {
        try (Connection connection = connection()) {
            UUID lotId = insertLot(connection);
            UUID first = insertSlot(connection, lotId, SLOT_ID);
            UUID second = insertSlot(connection, lotId, OTHER_SLOT_ID);
            insertReservation(connection, first, "2024-01-15T09:00:00+07:00", "2024-01-15T11:00:00+07:00", "PENDING");
            insertReservation(connection, second, "2024-01-15T10:00:00+07:00", "2024-01-15T12:00:00+07:00", "PENDING");
        }
    }

    @Test
    void twentyConcurrentOverlappingReservationsYieldOneSuccess() throws Exception {
        UUID slotId;
        try (Connection connection = connection()) {
            UUID lotId = insertLot(connection);
            slotId = insertSlot(connection, lotId, SLOT_ID);
        }

        CyclicBarrier barrier = new CyclicBarrier(CONCURRENT_ATTEMPTS);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_ATTEMPTS);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int index = 0; index < CONCURRENT_ATTEMPTS; index++) {
                results.add(executor.submit(() -> concurrentInsert(slotId, barrier)));
            }

            int successes = 0;
            int conflicts = 0;
            for (Future<String> result : results) {
                String sqlState = result.get(TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (sqlState == null) {
                    successes++;
                } else if (EXCLUSION_SQL_STATE.equals(sqlState)) {
                    conflicts++;
                }
            }

            assertEquals(1, successes);
            assertEquals(CONCURRENT_ATTEMPTS - 1, conflicts);
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
    }
}
