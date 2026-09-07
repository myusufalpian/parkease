package id.xyz.parkease.db;

import id.xyz.parkease.domain.ParkingLot;
import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.domain.Reservation;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Tag("postgres")
class PostgresJpaPersistenceIT {

    private static final String POSTGRES_IMAGE = "postgres:16-alpine";
    private static final String LOT_NAME = "JPA Lot";
    private static final String TIMEZONE = "Asia/Jakarta";
    private static final String OPERATING_HOURS = "{\"weekday\":\"08:00-17:00\"}";
    private static final String PRICING_SNAPSHOT = "{\"rateCardVersion\":1}";
    private static final String SLOT_ID = "JPA-01";
    private static final String VEHICLE_TYPE = "CAR";
    private static final int FLOOR = 1;
    private static final OffsetDateTime PLANNED_START = OffsetDateTime.parse("2024-01-15T09:00:00+07:00");
    private static final OffsetDateTime PLANNED_END = OffsetDateTime.parse("2024-01-15T11:00:00+07:00");

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE);

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        PostgresIntegrationSupport.requireDockerOrSkip();
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void persistsAndReloadsJsonbValuesAndSlotVersion() {
        ParkingLot lot = ParkingLot.builder()
                .name(LOT_NAME)
                .timezone(TIMEZONE)
                .operatingHours(OPERATING_HOURS)
                .build();
        entityManager.persist(lot);

        ParkingSlot slot = ParkingSlot.builder()
                .lot(lot)
                .slotId(SLOT_ID)
                .vehicleType(VEHICLE_TYPE)
                .floor(FLOOR)
                .build();
        entityManager.persist(slot);

        Reservation reservation = Reservation.builder()
                .slot(slot)
                .plannedStart(PLANNED_START)
                .plannedEnd(PLANNED_END)
                .pricingSnapshotJson(PRICING_SNAPSHOT)
                .build();
        entityManager.persist(reservation);
        entityManager.flush();
        entityManager.clear();

        ParkingLot loadedLot = entityManager.find(ParkingLot.class, lot.getId());
        ParkingSlot loadedSlot = entityManager.find(ParkingSlot.class, slot.getId());
        Reservation loadedReservation = entityManager.find(Reservation.class, reservation.getId());

        assertNotNull(loadedLot);
        assertNotNull(loadedSlot);
        assertNotNull(loadedReservation);
        assertEquals(OPERATING_HOURS, loadedLot.getOperatingHours());
        assertEquals(Long.valueOf(0), loadedSlot.getVersion());
        assertEquals(PRICING_SNAPSHOT, loadedReservation.getPricingSnapshotJson());
    }
}
