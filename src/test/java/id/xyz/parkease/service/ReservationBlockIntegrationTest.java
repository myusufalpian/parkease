package id.xyz.parkease.service;

import id.xyz.parkease.config.BookingProperties;
import id.xyz.parkease.domain.ParkingLot;
import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.domain.ParkingSlotBlock;
import id.xyz.parkease.domain.Reservation.Status;
import id.xyz.parkease.dto.AvailabilityResponse;
import id.xyz.parkease.dto.ReservationRequest;
import id.xyz.parkease.dto.ReservationResponse;
import id.xyz.parkease.exception.BusinessValidationException;
import id.xyz.parkease.exception.ConflictException;
import id.xyz.parkease.mapper.BlockMapper;
import id.xyz.parkease.mapper.ReservationMapper;
import id.xyz.parkease.repository.ParkingLotRepository;
import id.xyz.parkease.repository.ParkingSlotBlockRepository;
import id.xyz.parkease.repository.ParkingSlotRepository;
import id.xyz.parkease.repository.ReservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
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
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ReservationService.class, ReservationMapper.class, BookingWindowValidator.class, InventoryConflictService.class, BlockMapper.class, ReservationBlockIntegrationTest.TestBeans.class})
class ReservationBlockIntegrationTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2024-01-15T09:00:00+07:00");
    private static final OffsetDateTime END = OffsetDateTime.parse("2024-01-15T11:00:00+07:00");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2024-01-15T01:00:00Z");

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ParkingLotRepository lotRepository;

    @Autowired
    private ParkingSlotRepository slotRepository;

    @Autowired
    private ParkingSlotBlockRepository blockRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @BeforeEach
    void clean() {
        reservationRepository.deleteAll();
        blockRepository.deleteAll();
        slotRepository.deleteAll();
        lotRepository.deleteAll();
    }

    @Test
    void bookingFailsWhenSlotBlocked() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        blockRepository.saveAndFlush(ParkingSlotBlock.builder().slot(slot).blockedStart(START).blockedEnd(END).reason("r").build());
        ReservationRequest request = new ReservationRequest(lot.getId(), "CAR", "PLATE", START, END);
        assertThrows(ConflictException.class, () -> reservationService.createReservation(request, NOW));
    }

    @Test
    void bookingExcludesMaintenanceSlot() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).status(ParkingSlot.SlotStatus.MAINTENANCE).build());
        ReservationRequest request = new ReservationRequest(lot.getId(), "CAR", "PLATE", START, END);
        assertThrows(ConflictException.class, () -> reservationService.createReservation(request, NOW));
    }

    @Test
    void availabilityExcludesBlockedSlot() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot1 = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        ParkingSlot slot2 = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-02").vehicleType("CAR").floor(1).build());
        blockRepository.saveAndFlush(ParkingSlotBlock.builder().slot(slot1).blockedStart(START).blockedEnd(END).reason("r").build());
        AvailabilityResponse availability = reservationService.getAvailability(lot.getId(), START, END, "CAR");
        assertEquals(1, availability.slots().size());
        assertEquals("A-02", availability.slots().getFirst().slotId());
    }

    @Test
    void availabilityExcludesMaintenanceSlot() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).status(ParkingSlot.SlotStatus.MAINTENANCE).build());
        ParkingSlot avail = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-02").vehicleType("CAR").floor(1).build());
        AvailabilityResponse availability = reservationService.getAvailability(lot.getId(), START, END, "CAR");
        assertEquals(1, availability.slots().size());
        assertEquals("A-02", availability.slots().getFirst().slotId());
    }

    @Test
    void extendFailsWhenBlocked() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        ReservationResponse created = reservationService.createReservation(new ReservationRequest(lot.getId(), "CAR", "PLATE", START, END), NOW);
        ParkingSlot slot = slotRepository.findByLot_IdOrderByFloorAscSlotIdAsc(lot.getId()).getFirst();
        blockRepository.saveAndFlush(ParkingSlotBlock.builder().slot(slot).blockedStart(END).blockedEnd(END.plusHours(2)).reason("r").build());
        assertThrows(ConflictException.class, () -> reservationService.extend(created.id(), END.plusHours(1), NOW));
    }

    @Test
    void bookingRejectsPastStart() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        OffsetDateTime pastStart = NOW.minusHours(1);
        OffsetDateTime pastEnd = NOW.plusHours(1);
        ReservationRequest request = new ReservationRequest(lot.getId(), "CAR", "PLATE", pastStart, pastEnd);
        assertThrows(BusinessValidationException.class, () -> reservationService.createReservation(request, NOW));
    }

    @Test
    void bookingRejectsBelowMinimumDuration() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        OffsetDateTime start = NOW.plusHours(1);
        OffsetDateTime end = start.plusMinutes(10);
        ReservationRequest request = new ReservationRequest(lot.getId(), "CAR", "PLATE", start, end);
        assertThrows(BusinessValidationException.class, () -> reservationService.createReservation(request, NOW));
    }

    @Test
    void bookingRejectsBeyondHorizon() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        OffsetDateTime start = NOW.plusDays(91);
        OffsetDateTime end = start.plusHours(1);
        ReservationRequest request = new ReservationRequest(lot.getId(), "CAR", "PLATE", start, end);
        assertThrows(BusinessValidationException.class, () -> reservationService.createReservation(request, NOW));
    }

    @TestConfiguration
    static class TestBeans {
        @Bean BookingProperties bookingProperties() { return new BookingProperties(30, 90); }
        @Bean @Primary Clock fixedClock() { return Clock.fixed(Instant.parse("2024-01-15T01:00:00Z"), ZoneOffset.UTC); }
        @Bean @Primary ApplicationEventPublisher publisher() {
            return new ApplicationEventPublisher() {
                private final List<Object> events = new ArrayList<>();
                @Override public void publishEvent(Object event) { events.add(event); }
            };
        }
    }
}
