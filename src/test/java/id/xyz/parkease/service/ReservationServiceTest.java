package id.xyz.parkease.service;

import id.xyz.parkease.domain.ParkingLot;
import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.domain.ParkingSlot.SlotStatus;
import id.xyz.parkease.domain.Reservation;
import id.xyz.parkease.domain.Reservation.Status;
import id.xyz.parkease.dto.ReservationRequest;
import id.xyz.parkease.dto.ReservationResponse;
import id.xyz.parkease.dto.AvailabilityResponse;
import id.xyz.parkease.event.ReservationCheckedOutEvent;
import id.xyz.parkease.exception.ConflictException;
import id.xyz.parkease.mapper.ReservationMapper;
import id.xyz.parkease.repository.ParkingLotRepository;
import id.xyz.parkease.repository.ParkingSlotRepository;
import id.xyz.parkease.repository.ReservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ReservationService.class, ReservationMapper.class, ReservationServiceTest.TestBeans.class})
class ReservationServiceTest {

    private static final OffsetDateTime PLANNED_START = OffsetDateTime.parse("2024-01-15T09:00:00+07:00");
    private static final OffsetDateTime PLANNED_END = OffsetDateTime.parse("2024-01-15T11:00:00+07:00");
    private static final OffsetDateTime CHECK_IN = OffsetDateTime.parse("2024-01-15T09:15:00+07:00");
    private static final OffsetDateTime CHECK_OUT = OffsetDateTime.parse("2024-01-15T10:30:00+07:00");
    private static final OffsetDateTime FIXED_NOW = OffsetDateTime.parse("2024-01-15T08:00:00Z");
    private static final String VEHICLE_TYPE = "CAR";
    private static final String LOT_TIMEZONE = "Asia/Jakarta";

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ParkingLotRepository parkingLotRepository;

    @Autowired
    private ParkingSlotRepository parkingSlotRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private RecordingPublisher eventPublisher;

    @BeforeEach
    void cleanDatabase() {
        reservationRepository.deleteAll();
        parkingSlotRepository.deleteAll();
        parkingLotRepository.deleteAll();
        eventPublisher.events.clear();
    }

    @Test
    void createSelectsLowestFloorThenSlotIdAndReservesSlot() {
        ParkingLot lot = lot();
        ParkingSlot higherFloor = slot(lot, "B-01", 2);
        ParkingSlot lowestSlot = slot(lot, "A-02", 1);
        ParkingSlot lowestId = slot(lot, "A-01", 1);
        parkingSlotRepository.saveAllAndFlush(List.of(higherFloor, lowestSlot, lowestId));

        ReservationResponse response = reservationService.createReservation(request(lot, "PLATE-1"), FIXED_NOW);

        assertEquals(Status.PENDING, response.status());
        assertEquals("A-01", response.slot().slotId());
        assertEquals(SlotStatus.RESERVED, parkingSlotRepository.findById(lowestId.getId()).orElseThrow().getStatus());
        assertEquals(SlotStatus.AVAILABLE, parkingSlotRepository.findById(lowestSlot.getId()).orElseThrow().getStatus());
        assertEquals(SlotStatus.AVAILABLE, parkingSlotRepository.findById(higherFloor.getId()).orElseThrow().getStatus());
    }

    @Test
    void createRejectsWhenNoMatchingSlotRemainsAvailable() {
        ParkingLot lot = lot();
        parkingSlotRepository.saveAndFlush(slot(lot, "A-01", 1));
        reservationService.createReservation(request(lot, "PLATE-1"), FIXED_NOW);

        assertThrows(
                ConflictException.class,
                () -> reservationService.createReservation(request(lot, "PLATE-2"), FIXED_NOW));
    }

    @Test
    void checkInWithinGraceActivatesReservationAndOccupiesSlot() {
        ParkingLot lot = lot();
        parkingSlotRepository.saveAndFlush(slot(lot, "A-01", 1));
        ReservationResponse created = reservationService.createReservation(request(lot, "PLATE-1"), FIXED_NOW);

        ReservationResponse checkedIn = reservationService.checkIn(created.id(), CHECK_IN);

        assertEquals(Status.ACTIVE, checkedIn.status());
        assertEquals(CHECK_IN, checkedIn.actualStart());
        assertEquals(SlotStatus.OCCUPIED, parkingSlotRepository.findById(checkedIn.slot().id()).orElseThrow().getStatus());
    }

    @Test
    void lateCheckInMarksNoShowAndReleasesSlotAtGraceBoundary() {
        ParkingLot lot = lot();
        ParkingSlot slot = slot(lot, "A-01", 1);
        parkingSlotRepository.saveAndFlush(slot);
        ReservationResponse created = reservationService.createReservation(request(lot, "PLATE-1"), FIXED_NOW);

        ReservationResponse noShow = reservationService.checkIn(
                created.id(),
                PLANNED_START.plusMinutes(31));

        assertEquals(Status.NO_SHOW, noShow.status());
        assertEquals(PLANNED_START.plusMinutes(30), noShow.actualStart());
        assertEquals(SlotStatus.AVAILABLE, parkingSlotRepository.findById(slot.getId()).orElseThrow().getStatus());
    }

    @Test
    void checkOutCompletesReservationReleasesSlotAndPublishesOneEvent() {
        ParkingLot lot = lot();
        parkingSlotRepository.saveAndFlush(slot(lot, "A-01", 1));
        ReservationResponse created = reservationService.createReservation(request(lot, "PLATE-1"), FIXED_NOW);
        reservationService.checkIn(created.id(), CHECK_IN);

        ReservationResponse completed = reservationService.checkOut(created.id(), CHECK_OUT);

        assertEquals(Status.COMPLETED, completed.status());
        assertEquals(CHECK_OUT, completed.actualEnd());
        assertEquals(1, eventPublisher.events.size());
        assertTrue(eventPublisher.events.getFirst() instanceof ReservationCheckedOutEvent);
        assertEquals(SlotStatus.AVAILABLE, parkingSlotRepository.findById(completed.slot().id()).orElseThrow().getStatus());
    }

    @Test
    void completedCheckoutRetryReturnsExistingResultWithoutPublishingAnotherEvent() {
        ParkingLot lot = lot();
        parkingSlotRepository.saveAndFlush(slot(lot, "A-01", 1));
        ReservationResponse created = reservationService.createReservation(request(lot, "PLATE-1"), FIXED_NOW);
        reservationService.checkIn(created.id(), CHECK_IN);
        ReservationResponse first = reservationService.checkOut(created.id(), CHECK_OUT);

        ReservationResponse retry = reservationService.checkOut(created.id(), CHECK_OUT.plusMinutes(10));

        assertEquals(first, retry);
        assertEquals(1, eventPublisher.events.size());
    }

    @Test
    void pendingCancellationReleasesSlotAndTerminalRetryIsStable() {
        ParkingLot lot = lot();
        parkingSlotRepository.saveAndFlush(slot(lot, "A-01", 1));
        ReservationResponse created = reservationService.createReservation(request(lot, "PLATE-1"), FIXED_NOW);

        ReservationResponse cancelled = reservationService.cancel(created.id(), "customer request", FIXED_NOW);
        ReservationResponse retry = reservationService.cancel(created.id(), "different reason", FIXED_NOW.plusMinutes(1));

        assertEquals(Status.CANCELLED, cancelled.status());
        assertEquals("customer request", cancelled.cancellationReason());
        assertFalse(cancelled.lateCancellation());
        assertEquals(cancelled, retry);
    }

    @Test
    void activeCancellationWithinGraceMarksLateCancellation() {
        ParkingLot lot = lot();
        parkingSlotRepository.saveAndFlush(slot(lot, "A-01", 1));
        ReservationResponse created = reservationService.createReservation(request(lot, "PLATE-1"), FIXED_NOW);
        reservationService.checkIn(created.id(), CHECK_IN);

        ReservationResponse cancelled = reservationService.cancel(
                created.id(), "left early", PLANNED_START.plusMinutes(30));

        assertEquals(Status.CANCELLED, cancelled.status());
        assertTrue(cancelled.lateCancellation());
    }

    @Test
    void activeCancellationAfterGraceIsRejected() {
        ParkingLot lot = lot();
        parkingSlotRepository.saveAndFlush(slot(lot, "A-01", 1));
        ReservationResponse created = reservationService.createReservation(request(lot, "PLATE-1"), FIXED_NOW);
        reservationService.checkIn(created.id(), CHECK_IN);

        assertThrows(
                ConflictException.class,
                () -> reservationService.cancel(created.id(), "too late", PLANNED_START.plusMinutes(31)));
    }

    @Test
    void extendUpdatesEndWhenNoConflictExists() {
        ParkingLot lot = lot();
        parkingSlotRepository.saveAndFlush(slot(lot, "A-01", 1));
        ReservationResponse created = reservationService.createReservation(request(lot, "PLATE-1"), FIXED_NOW);
        OffsetDateTime extendedEnd = OffsetDateTime.parse("2024-01-15T12:00:00+07:00");

        ReservationResponse extended = reservationService.extend(created.id(), extendedEnd, FIXED_NOW);

        assertEquals(extendedEnd, extended.plannedEnd());
        assertEquals(extendedEnd, reservationRepository.findById(created.id()).orElseThrow().getPlannedEnd());
    }

    @Test
    void extendRejectsOverlappingActiveReservationOnSameSlot() {
        ParkingLot lot = lot();
        ParkingSlot slot = slot(lot, "A-01", 1);
        parkingSlotRepository.saveAndFlush(slot);
        ReservationResponse first = reservationService.createReservation(request(lot, "PLATE-1"), FIXED_NOW);
        Reservation second = Reservation.builder()
                .slot(slot)
                .customerPlate("PLATE-2")
                .plannedStart(OffsetDateTime.parse("2024-01-15T10:30:00+07:00"))
                .plannedEnd(OffsetDateTime.parse("2024-01-15T12:00:00+07:00"))
                .status(Status.PENDING)
                .build();
        reservationRepository.saveAndFlush(second);

        assertThrows(
                ConflictException.class,
                () -> reservationService.extend(
                        first.id(), OffsetDateTime.parse("2024-01-15T11:30:00+07:00"), FIXED_NOW));
    }

    @Test
    void extendAdvancesUpdatedAtTimestamp() {
        ParkingLot lot = lot();
        parkingSlotRepository.saveAndFlush(slot(lot, "A-01", 1));
        ReservationResponse created = reservationService.createReservation(request(lot, "PLATE-1"), FIXED_NOW);
        OffsetDateTime beforeExtend = reservationRepository.findById(created.id()).orElseThrow().getUpdatedAt();
        OffsetDateTime extendedEnd = OffsetDateTime.parse("2024-01-15T12:00:00+07:00");

        reservationService.extend(created.id(), extendedEnd, FIXED_NOW);

        Reservation reloaded = reservationRepository.findById(created.id()).orElseThrow();
        assertEquals(extendedEnd, reloaded.getPlannedEnd());
        assertFalse(reloaded.getUpdatedAt().isBefore(beforeExtend));
    }

    @Test
    void availabilityExcludesSlotWithOverlappingReservation() {
        ParkingLot lot = lot();
        ParkingSlot taken = slot(lot, "A-01", 1);
        ParkingSlot free = slot(lot, "A-02", 1);
        parkingSlotRepository.saveAllAndFlush(List.of(taken, free));
        reservationRepository.saveAndFlush(Reservation.builder()
                .slot(taken)
                .customerPlate("PLATE-1")
                .plannedStart(PLANNED_START)
                .plannedEnd(PLANNED_END)
                .status(Status.PENDING)
                .build());

        AvailabilityResponse availability = reservationService.getAvailability(
                lot.getId(), PLANNED_START, PLANNED_END, VEHICLE_TYPE);

        assertEquals(1, availability.slots().size());
        assertEquals("A-02", availability.slots().getFirst().slotId());
    }

    @Test
    void availabilityIncludesSlotWhenRequestedWindowTouchesExistingReservation() {
        ParkingLot lot = lot();
        ParkingSlot slot = slot(lot, "A-01", 1);
        parkingSlotRepository.saveAndFlush(slot);
        reservationRepository.saveAndFlush(Reservation.builder()
                .slot(slot)
                .customerPlate("PLATE-1")
                .plannedStart(PLANNED_START)
                .plannedEnd(PLANNED_END)
                .status(Status.PENDING)
                .build());
        OffsetDateTime touchingStart = PLANNED_END;
        OffsetDateTime touchingEnd = OffsetDateTime.parse("2024-01-15T13:00:00+07:00");

        AvailabilityResponse availability = reservationService.getAvailability(
                lot.getId(), touchingStart, touchingEnd, VEHICLE_TYPE);

        assertEquals(1, availability.slots().size());
        assertEquals("A-01", availability.slots().getFirst().slotId());
    }

    private ParkingLot lot() {
        return parkingLotRepository.saveAndFlush(
                ParkingLot.builder().name("Test Lot").timezone(LOT_TIMEZONE).build());
    }

    private ParkingSlot slot(ParkingLot lot, String slotId, int floor) {
        return ParkingSlot.builder()
                .lot(lot)
                .slotId(slotId)
                .vehicleType(VEHICLE_TYPE)
                .floor(floor)
                .build();
    }

    private ReservationRequest request(ParkingLot lot, String plate) {
        return new ReservationRequest(lot.getId(), VEHICLE_TYPE, plate, PLANNED_START, PLANNED_END);
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2024-01-15T08:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        @Primary
        RecordingPublisher recordingPublisher() {
            return new RecordingPublisher();
        }
    }

    static final class RecordingPublisher implements ApplicationEventPublisher {

        private final List<Object> events = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {
            events.add(event);
        }
    }
}
