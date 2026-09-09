package id.xyz.parkease.service;

import id.xyz.parkease.config.BookingProperties;
import id.xyz.parkease.domain.ParkingLot;
import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.dto.BlockResponse;
import id.xyz.parkease.dto.CreateBlockRequest;
import id.xyz.parkease.mapper.BlockMapper;
import id.xyz.parkease.repository.AuditRecordRepository;
import id.xyz.parkease.repository.ParkingLotRepository;
import id.xyz.parkease.repository.ParkingSlotBlockRepository;
import id.xyz.parkease.repository.ParkingSlotRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SlotBlockService.class, InventoryConflictService.class, BlockMapper.class, AuditService.class,
        BookingWindowValidator.class, SlotBlockServiceSecurityTest.TestBeans.class})
class SlotBlockServiceSecurityTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2024-01-15T09:00:00+07:00");
    private static final OffsetDateTime END = OffsetDateTime.parse("2024-01-15T11:00:00+07:00");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2024-01-15T01:00:00Z");
    private static final UUID ACTOR = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private SlotBlockService slotBlockService;

    @Autowired
    private ParkingLotRepository lotRepository;

    @Autowired
    private ParkingSlotRepository slotRepository;

    @Autowired
    private ParkingSlotBlockRepository blockRepository;

    @Autowired
    private AuditRecordRepository auditRecordRepository;

    @BeforeEach
    void clean() {
        blockRepository.deleteAll();
        auditRecordRepository.deleteAll();
        slotRepository.deleteAll();
        lotRepository.deleteAll();
    }

    @Test
    void sanitizesReasonWithNewlineAndTabBeforePersistAndAudit() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        String malicious = "clean\ninjected\ttab\r";
        BlockResponse response = slotBlockService.create(slot.getId(), new CreateBlockRequest(START, END, malicious), ACTOR, NOW);
        assertEquals("clean_injected_tab_", response.reason());
        assertFalse(response.reason().contains("\n"));
        assertFalse(response.reason().contains("\r"));
        assertFalse(response.reason().contains("\t"));
        var audit = auditRecordRepository.findAll().getFirst();
        assertEquals("clean_injected_tab_", audit.getReason());
        String target = audit.getTarget();
        org.junit.jupiter.api.Assertions.assertTrue(target.contains("lot:" + lot.getId().toString()));
        org.junit.jupiter.api.Assertions.assertTrue(target.contains("block:" + response.id().toString()));
    }

    @Test
    void deleteAuditContainsLotIdAndSanitizedReason() {
        ParkingLot lot = lotRepository.saveAndFlush(ParkingLot.builder().name("L").timezone("Asia/Jakarta").build());
        ParkingSlot slot = slotRepository.saveAndFlush(ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build());
        BlockResponse created = slotBlockService.create(slot.getId(), new CreateBlockRequest(START, END, "reason\nbad"), ACTOR, NOW);
        auditRecordRepository.deleteAll();
        BlockResponse deleted = slotBlockService.delete(created.id(), ACTOR, NOW);
        assertEquals("CANCELLED", deleted.status());
        var audit = auditRecordRepository.findAll().getFirst();
        String target = audit.getTarget();
        org.junit.jupiter.api.Assertions.assertTrue(target.contains("lot:" + lot.getId().toString()));
        org.junit.jupiter.api.Assertions.assertTrue(target.contains("block:" + created.id().toString()));
        assertEquals("reason_bad", audit.getReason());
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        BookingProperties bookingProperties() {
            return new BookingProperties(30, 90);
        }

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2024-01-15T01:00:00Z"), ZoneOffset.UTC);
        }
    }
}
