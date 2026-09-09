package id.xyz.parkease.service;

import id.xyz.parkease.domain.ParkingLot;
import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.domain.ParkingSlotBlock;
import id.xyz.parkease.domain.Reservation;
import id.xyz.parkease.domain.Reservation.Status;
import id.xyz.parkease.exception.ConflictException;
import id.xyz.parkease.mapper.BlockMapper;
import id.xyz.parkease.repository.ParkingLotRepository;
import id.xyz.parkease.repository.ParkingSlotBlockRepository;
import id.xyz.parkease.repository.ParkingSlotRepository;
import id.xyz.parkease.repository.ReservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import id.xyz.parkease.config.BookingProperties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({InventoryConflictService.class, BlockMapper.class, InventoryConflictServiceTest.TestBeans.class})
class InventoryConflictServiceTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2024-01-15T09:00:00+07:00");
    private static final OffsetDateTime END = OffsetDateTime.parse("2024-01-15T11:00:00+07:00");

    @Autowired
    private InventoryConflictService service;

    @Autowired
    private ParkingLotRepository lotRepository;

    @Autowired
    private ParkingSlotRepository slotRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ParkingSlotBlockRepository blockRepository;

    @BeforeEach
    void clean() {
        reservationRepository.deleteAll();
        blockRepository.deleteAll();
        slotRepository.deleteAll();
        lotRepository.deleteAll();
    }

    @Test
    void detectsOverlappingReservation() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        reservationRepository.saveAndFlush(Reservation.builder().slot(slot).customerPlate("P").plannedStart(START).plannedEnd(END).status(Status.PENDING).build());
        assertTrue(service.hasOverlappingReservation(slot.getId(), START, END, null));
    }

    @Test
    void adjacentReservationIsNotOverlapping() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        reservationRepository.saveAndFlush(Reservation.builder().slot(slot).customerPlate("P").plannedStart(START).plannedEnd(END).status(Status.PENDING).build());
        assertFalse(service.hasOverlappingReservation(slot.getId(), END, END.plusHours(1), null));
    }

    @Test
    void detectsActiveBlockOverlap() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        blockRepository.saveAndFlush(ParkingSlotBlock.builder().slot(slot).blockedStart(START).blockedEnd(END).reason("maintenance").build());
        assertTrue(service.hasActiveBlockOverlap(slot.getId(), START, END));
    }

    @Test
    void adjacentBlockIsNotOverlapping() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        blockRepository.saveAndFlush(ParkingSlotBlock.builder().slot(slot).blockedStart(START).blockedEnd(END).reason("r").build());
        assertFalse(service.hasActiveBlockOverlap(slot.getId(), END, END.plusHours(1)));
    }

    @Test
    void maintenanceSlotIsNotAvailable() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).status(ParkingSlot.SlotStatus.MAINTENANCE).build());
        assertFalse(service.isSlotAvailableForWindow(slot, START, END));
    }

    @Test
    void requireNoConflictThrowsWhenBlocked() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        blockRepository.saveAndFlush(ParkingSlotBlock.builder().slot(slot).blockedStart(START).blockedEnd(END).reason("r").build());
        assertThrows(ConflictException.class, () -> service.requireNoConflictForReservation(slot.getId(), START, END, null));
    }

    @Test
    void requireNoConflictForBlockThrowsWhenReservationExists() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        reservationRepository.saveAndFlush(Reservation.builder().slot(slot).customerPlate("P").plannedStart(START).plannedEnd(END).status(Status.PENDING).build());
        assertThrows(ConflictException.class, () -> service.requireNoConflictForBlock(slot.getId(), START, END));
    }

    @Test
    void requireNoConflictForReservationThrowsWhenReservationExists() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        reservationRepository.saveAndFlush(Reservation.builder().slot(slot).customerPlate("P").plannedStart(START).plannedEnd(END).status(Status.PENDING).build());
        assertThrows(ConflictException.class, () -> service.requireNoConflictForReservation(slot.getId(), START, END, null));
    }

    @Test
    void isSlotAvailableFalseWhenOverlappingReservation() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        reservationRepository.saveAndFlush(Reservation.builder().slot(slot).customerPlate("P").plannedStart(START).plannedEnd(END).status(Status.PENDING).build());
        assertFalse(service.isSlotAvailableForWindow(slot, START, END));
    }

    @Test
    void isSlotAvailableFalseWhenBlocked() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        blockRepository.saveAndFlush(ParkingSlotBlock.builder().slot(slot).blockedStart(START).blockedEnd(END).reason("r").build());
        assertFalse(service.isSlotAvailableForWindow(slot, START, END));
    }

    @Test
    void isSlotAvailableTrueWhenNoConflict() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        assertTrue(service.isSlotAvailableForWindow(slot, START, END));
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        BookingProperties bookingProperties() { return new BookingProperties(30, 90); }

        @Bean
        @Primary
        Clock fixedClock() { return Clock.fixed(Instant.parse("2024-01-15T01:00:00Z"), ZoneOffset.UTC); }
    }
}
