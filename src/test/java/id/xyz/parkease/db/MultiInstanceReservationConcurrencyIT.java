package id.xyz.parkease.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("postgres")
class MultiInstanceReservationConcurrencyIT {

    private static final String POSTGRES_IMAGE = "postgres:16-alpine";
    private static final String EXCLUSION_SQL_STATE = "23P01";
    private static final String LOT_NAME = "Multi-instance Lot";
    private static final String LOT_TIMEZONE = "Asia/Jakarta";
    private static final String VEHICLE_TYPE = "CAR";
    private static final int FLOOR = 1;
    private static final int INSTANCE_COUNT = 20;
    private static final long TIMEOUT_SECONDS = 30L;
    private static final String OVERLAPPING_START = "2024-01-15T09:00:00+07:00";
    private static final String OVERLAPPING_END = "2024-01-15T11:00:00+07:00";
    private static final String TOUCHING_START = "2024-01-15T11:00:00+07:00";
    private static final String TOUCHING_END = "2024-01-15T13:00:00+07:00";

    private static PostgreSQLContainer<?> postgres;
    private final List<HikariDataSource> independentPools = new ArrayList<>();

    @BeforeAll
    static void startPostgres() {
        PostgresIntegrationSupport.requireDockerOrSkip();
        postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE);
        postgres.start();
    }

    @AfterAll
    static void stopPostgres() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void migrateFreshSchema() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @AfterEach
    void closeIndependentPools() {
        independentPools.forEach(HikariDataSource::close);
        independentPools.clear();
    }

    @Test
    void twentyIndependentPoolsYieldExactlyOneWinnerAndNineteenExclusionConflicts() throws Exception {
        UUID slotId = createSlot();
        CyclicBarrier barrier = new CyclicBarrier(INSTANCE_COUNT);
        ExecutorService executor = Executors.newFixedThreadPool(INSTANCE_COUNT);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int index = 0; index < INSTANCE_COUNT; index++) {
                HikariDataSource pool = independentPool();
                UUID reservationId = UUID.randomUUID();
                results.add(executor.submit(() -> insertReservation(
                        pool, barrier, slotId, reservationId, OVERLAPPING_START, OVERLAPPING_END)));
            }

            int successes = 0;
            int conflicts = 0;
            for (Future<String> result : results) {
                String sqlState = result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (sqlState == null) {
                    successes++;
                } else if (EXCLUSION_SQL_STATE.equals(sqlState)) {
                    conflicts++;
                }
            }

            assertEquals(1, successes);
            assertEquals(INSTANCE_COUNT - 1, conflicts);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
    }

    @Test
    void independentPoolsAllowTouchingHalfOpenEndpoints() throws Exception {
        UUID slotId = createSlot();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                HikariDataSource pool = independentPool();
                UUID reservationId = UUID.randomUUID();
                String start = index == 0 ? OVERLAPPING_START : TOUCHING_START;
                String end = index == 0 ? TOUCHING_START : TOUCHING_END;
                results.add(executor.submit(() -> insertReservation(
                        pool, barrier, slotId, reservationId, start, end)));
            }

            int successes = 0;
            int conflicts = 0;
            for (Future<String> result : results) {
                String sqlState = result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (sqlState == null) {
                    successes++;
                } else if (EXCLUSION_SQL_STATE.equals(sqlState)) {
                    conflicts++;
                }
            }

            assertEquals(2, successes);
            assertEquals(0, conflicts);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
    }

    private HikariDataSource independentPool() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(TIMEOUT_SECONDS * 1_000L);
        HikariDataSource pool = new HikariDataSource(config);
        independentPools.add(pool);
        return pool;
    }

    private UUID createSlot() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            UUID lotId = UUID.randomUUID();
            UUID slotId = UUID.randomUUID();
            try (PreparedStatement lot = connection.prepareStatement(
                    "INSERT INTO parking_lot (id, name, timezone) VALUES (?, ?, ?)")) {
                lot.setObject(1, lotId);
                lot.setString(2, LOT_NAME);
                lot.setString(3, LOT_TIMEZONE);
                assertEquals(1, lot.executeUpdate());
            }
            try (PreparedStatement slot = connection.prepareStatement(
                    "INSERT INTO parking_slot (id, lot_id, slot_id, vehicle_type, floor) VALUES (?, ?, ?, ?, ?)")) {
                slot.setObject(1, slotId);
                slot.setObject(2, lotId);
                slot.setString(3, "A-01");
                slot.setString(4, VEHICLE_TYPE);
                slot.setInt(5, FLOOR);
                assertEquals(1, slot.executeUpdate());
            }
            return slotId;
        }
    }

    private String insertReservation(
            HikariDataSource pool,
            CyclicBarrier barrier,
            UUID slotId,
            UUID reservationId,
            String plannedStart,
            String plannedEnd) throws Exception {
        try (Connection connection = pool.getConnection()) {
            barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO reservation (id, slot_id, planned_start, planned_end, status) "
                            + "VALUES (?, ?, ?::timestamptz, ?::timestamptz, 'PENDING')")) {
                statement.setObject(1, reservationId);
                statement.setObject(2, slotId);
                statement.setString(3, plannedStart);
                statement.setString(4, plannedEnd);
                statement.executeUpdate();
                return null;
            } catch (SQLException exception) {
                return exception.getSQLState();
            }
        }
    }
}
