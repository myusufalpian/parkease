package id.xyz.parkease.db;

import id.xyz.parkease.domain.BillingOperation.OperationType;
import id.xyz.parkease.domain.OutboxEvent;
import id.xyz.parkease.domain.OutboxEvent.EventType;
import id.xyz.parkease.domain.ParkingInvoice;
import id.xyz.parkease.domain.ParkingInvoice.PaymentStatus;
import id.xyz.parkease.domain.ParkingLot;
import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.domain.Reservation;
import id.xyz.parkease.domain.Reservation.Status;
import id.xyz.parkease.repository.BillingOperationRepository;
import id.xyz.parkease.repository.OutboxEventRepository;
import id.xyz.parkease.repository.ParkingInvoiceRepository;
import id.xyz.parkease.repository.ParkingLotRepository;
import id.xyz.parkease.repository.ParkingSlotRepository;
import id.xyz.parkease.repository.ReservationRepository;
import id.xyz.parkease.service.OutboxDispatcher;
import java.math.BigDecimal;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Tag("postgres")
class OutboxDispatcherPostgresIT {

    private static final String POSTGRES_IMAGE = "postgres:16-alpine";
    private static final String LOT_TIMEZONE = "Asia/Jakarta";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE);

    @Autowired
    private OutboxDispatcher outboxDispatcher;

    @Autowired
    private ParkingLotRepository parkingLotRepository;

    @Autowired
    private ParkingSlotRepository parkingSlotRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ParkingInvoiceRepository parkingInvoiceRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private BillingOperationRepository billingOperationRepository;

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
        outboxEventRepository.deleteAll();
        billingOperationRepository.deleteAll();
        parkingInvoiceRepository.deleteAll();
        reservationRepository.deleteAll();
        parkingSlotRepository.deleteAll();
        parkingLotRepository.deleteAll();
    }

    @Test
    void paymentCaptureStoresProviderReferenceAsJsonbAndMarksInvoicePaid() {
        ParkingLot lot = parkingLotRepository.saveAndFlush(
                ParkingLot.builder().name("Lot").timezone(LOT_TIMEZONE).build());
        ParkingSlot slot = parkingSlotRepository.saveAndFlush(
                ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        Reservation reservation = reservationRepository.saveAndFlush(Reservation.builder()
                .slot(slot)
                .plannedStart(OffsetDateTime.parse("2024-01-15T09:00:00+07:00"))
                .plannedEnd(OffsetDateTime.parse("2024-01-15T10:00:00+07:00"))
                .status(Status.PENDING)
                .build());
        parkingInvoiceRepository.saveAndFlush(ParkingInvoice.builder()
                .reservation(reservation)
                .durationMinutes(60)
                .subtotal(new BigDecimal("10000.00"))
                .total(new BigDecimal("10000.00"))
                .currency("IDR")
                .build());
        OutboxEvent event = outboxEventRepository.saveAndFlush(OutboxEvent.builder()
                .eventType(EventType.PAYMENT_CAPTURE)
                .aggregateId(reservation.getId())
                .payloadJson("{}")
                .build());

        outboxDispatcher.dispatch(event);

        assertEquals(
                PaymentStatus.PAID,
                parkingInvoiceRepository.findByReservation_Id(reservation.getId()).orElseThrow().getPaymentStatus());
        assertTrue(billingOperationRepository
                .findByReservation_IdAndOperationType(reservation.getId(), OperationType.PAYMENT)
                .isPresent());
    }
}
