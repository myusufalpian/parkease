package id.xyz.parkease.service;

import id.xyz.parkease.config.BookingProperties;
import id.xyz.parkease.domain.ParkingLot;
import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.domain.ParkingSlotBlock.BlockStatus;
import id.xyz.parkease.domain.Reservation;
import id.xyz.parkease.domain.Reservation.Status;
import id.xyz.parkease.dto.BlockResponse;
import id.xyz.parkease.dto.CreateBlockRequest;
import id.xyz.parkease.exception.BusinessValidationException;
import id.xyz.parkease.exception.ConflictException;
import id.xyz.parkease.exception.ResourceNotFoundException;
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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SlotBlockService.class, InventoryConflictService.class, BlockMapper.class, AuditService.class,
        BookingWindowValidator.class, SlotBlockServiceTest.TestBeans.class})
class SlotBlockServiceTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2024-01-15T09:00:00+07:00");
    private static final OffsetDateTime END = OffsetDateTime.parse("2024-01-15T11:00:00+07:00");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2024-01-15T01:00:00Z");
    private static final UUID ACTOR = UUID.randomUUID();

    @Autowired
    private SlotBlockService slotBlockService;

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
    void createsBlockWhenNoConflict() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        BlockResponse response = slotBlockService.create(slot.getId(), new CreateBlockRequest(START, END, "cleaning"), ACTOR, NOW);
        assertEquals(BlockStatus.ACTIVE.name(), response.status());
        assertEquals("cleaning", response.reason());
    }

    @Test
    void rejectsInvalidInterval() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        assertThrows(BusinessValidationException.class,
                () -> slotBlockService.create(slot.getId(), new CreateBlockRequest(END, START, "r"), ACTOR, NOW));
    }

    @Test
    void rejectsWhenOverlappingReservationExists() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        reservationRepository.saveAndFlush(Reservation.builder().slot(slot).customerPlate("P").plannedStart(START).plannedEnd(END).status(Status.PENDING).build());
        assertThrows(ConflictException.class,
                () -> slotBlockService.create(slot.getId(), new CreateBlockRequest(START, END, "r"), ACTOR, NOW));
    }

    @Test
    void rejectsOverlappingBlock() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        slotBlockService.create(slot.getId(), new CreateBlockRequest(START, END, "r1"), ACTOR, NOW);
        assertThrows(ConflictException.class,
                () -> slotBlockService.create(slot.getId(), new CreateBlockRequest(START.plusMinutes(30), END.plusHours(1), "r2"), ACTOR, NOW));
    }

    @Test
    void allowsAdjacentBlock() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        slotBlockService.create(slot.getId(), new CreateBlockRequest(START, END, "r1"), ACTOR, NOW);
        BlockResponse second = slotBlockService.create(slot.getId(), new CreateBlockRequest(END, END.plusHours(1), "r2"), ACTOR, NOW);
        assertEquals(BlockStatus.ACTIVE.name(), second.status());
    }

    @Test
    void listEvaluatesExpiry() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        slotBlockService.create(slot.getId(), new CreateBlockRequest(START, END, "r"), ACTOR, NOW);
        List<BlockResponse> afterExpiry = slotBlockService.list(slot.getId(), END.plusMinutes(1));
        assertEquals(BlockStatus.EXPIRED.name(), afterExpiry.getFirst().status());
    }

    @Test
    void deleteCancelsActiveBlock() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        BlockResponse created = slotBlockService.create(slot.getId(), new CreateBlockRequest(START, END, "r"), ACTOR, NOW);
        BlockResponse cancelled = slotBlockService.delete(created.id(), ACTOR, NOW);
        assertEquals(BlockStatus.CANCELLED.name(), cancelled.status());
    }

    @Test
    void deleteExpiredBlockMarksExpired() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        BlockResponse created = slotBlockService.create(slot.getId(), new CreateBlockRequest(START, END, "r"), ACTOR, NOW);
        BlockResponse result = slotBlockService.delete(created.id(), ACTOR, END.plusMinutes(1));
        assertEquals(BlockStatus.EXPIRED.name(), result.status());
    }

    @Test
    void rejectsUnknownSlot() {
        assertThrows(ResourceNotFoundException.class,
                () -> slotBlockService.create(UUID.randomUUID(), new CreateBlockRequest(START, END, "r"), ACTOR, NOW));
    }

    @Test
    void createsViaDefaultClockDelegates() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        BlockResponse response = slotBlockService.create(slot.getId(), new CreateBlockRequest(START, END, "r"), ACTOR);
        assertEquals(BlockStatus.ACTIVE.name(), response.status());
    }

    @Test
    void deletesViaDefaultClockDelegates() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        BlockResponse created = slotBlockService.create(slot.getId(), new CreateBlockRequest(START, END, "r"), ACTOR, NOW);
        BlockResponse deleted = slotBlockService.delete(created.id(), ACTOR);
        assertEquals(BlockStatus.CANCELLED.name(), deleted.status());
    }

    @Test
    void listWithoutExpiryReturnsActive() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        slotBlockService.create(slot.getId(), new CreateBlockRequest(START, END, "r"), ACTOR, NOW);
        List<BlockResponse> active = slotBlockService.list(slot.getId(), NOW);
        assertEquals(BlockStatus.ACTIVE.name(), active.getFirst().status());
    }

    @Test
    void deleteAlreadyCancelledIsIdempotent() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        BlockResponse created = slotBlockService.create(slot.getId(), new CreateBlockRequest(START, END, "r"), ACTOR, NOW);
        slotBlockService.delete(created.id(), ACTOR, NOW);
        BlockResponse second = slotBlockService.delete(created.id(), ACTOR, NOW);
        assertEquals(BlockStatus.CANCELLED.name(), second.status());
    }

    @Test
    void listViaDefaultClockDelegates() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        slotBlockService.create(slot.getId(), new CreateBlockRequest(START, END, "r"), ACTOR, NOW);
        List<BlockResponse> response = slotBlockService.list(slot.getId());
        assertEquals(1, response.size());
    }

    @TestConfiguration
    static class TestBeans {
        @Bean BookingProperties bookingProperties() { return new BookingProperties(30, 90); }
        @Bean @Primary Clock fixedClock() { return Clock.fixed(Instant.parse("2024-01-15T01:00:00Z"), ZoneOffset.UTC); }
    }
}
