package id.xyz.parkease.db;

import id.xyz.parkease.domain.ParkingLot;
import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.domain.ParkingSlot.SlotStatus;
import id.xyz.parkease.domain.Reservation.Status;
import id.xyz.parkease.dto.ReservationRequest;
import id.xyz.parkease.dto.ReservationResponse;
import id.xyz.parkease.repository.ParkingLotRepository;
import id.xyz.parkease.repository.ParkingSlotRepository;
import id.xyz.parkease.repository.ReservationRepository;
import id.xyz.parkease.service.ReservationService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@Tag("postgres")
class ReservationLifecycleIT {

    private static final String POSTGRES_IMAGE = "postgres:16-alpine";
    private static final String LOT_TIMEZONE = "Asia/Jakarta";
    private static final String VEHICLE_TYPE = "CAR";
    private static final OffsetDateTime PLANNED_START = OffsetDateTime.parse("2024-01-15T09:00:00+07:00");
    private static final OffsetDateTime PLANNED_END = OffsetDateTime.parse("2024-01-15T11:00:00+07:00");
    private static final OffsetDateTime CHECK_IN = OffsetDateTime.parse("2024-01-15T09:15:00+07:00");
    private static final OffsetDateTime CHECK_OUT = OffsetDateTime.parse("2024-01-15T10:30:00+07:00");
    private static final OffsetDateTime REQUESTED_AT = OffsetDateTime.parse("2024-01-15T08:00:00Z");

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE);

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ParkingLotRepository parkingLotRepository;

    @Autowired
    private ParkingSlotRepository parkingSlotRepository;

    @Autowired
    private ReservationRepository reservationRepository;

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

    @AfterAll
    static void stopPostgres() {
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }

    @BeforeEach
    void cleanDatabase() {
        reservationRepository.deleteAll();
        parkingSlotRepository.deleteAll();
        parkingLotRepository.deleteAll();
    }

    @Test
    void reservationCompletesFullLifecycleThroughServiceAndPostgres() {
        ParkingLot lot = parkingLotRepository.saveAndFlush(
                ParkingLot.builder().name("Lifecycle Lot").timezone(LOT_TIMEZONE).build());
        ParkingSlot slot = parkingSlotRepository.saveAndFlush(ParkingSlot.builder()
                .lot(lot)
                .slotId("A-01")
                .vehicleType(VEHICLE_TYPE)
                .floor(1)
                .build());

        ReservationRequest request = new ReservationRequest(
                lot.getId(), VEHICLE_TYPE, "B1234XYZ", PLANNED_START, PLANNED_END);
        ReservationResponse created = reservationService.createReservation(request, REQUESTED_AT);
        ReservationResponse checkedIn = reservationService.checkIn(created.id(), CHECK_IN);
        ReservationResponse checkedOut = reservationService.checkOut(created.id(), CHECK_OUT);

        assertEquals(Status.PENDING, created.status());
        assertEquals("A-01", created.slot().slotId());
        assertEquals(Status.ACTIVE, checkedIn.status());
        assertEquals(CHECK_IN, checkedIn.actualStart());
        assertEquals(Status.COMPLETED, checkedOut.status());
        assertEquals(CHECK_OUT, checkedOut.actualEnd());
        assertEquals(SlotStatus.AVAILABLE, parkingSlotRepository.findById(slot.getId()).orElseThrow().getStatus());
        assertFalse(reservationRepository.findById(created.id()).orElseThrow().isLateCancellation());
    }
}
