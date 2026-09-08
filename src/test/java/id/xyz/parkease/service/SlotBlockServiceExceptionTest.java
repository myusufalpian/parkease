package id.xyz.parkease.service;

import id.xyz.parkease.domain.ParkingLot;
import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.dto.CreateBlockRequest;
import id.xyz.parkease.exception.ConflictException;
import id.xyz.parkease.mapper.BlockMapper;
import id.xyz.parkease.repository.ParkingSlotBlockRepository;
import id.xyz.parkease.repository.ParkingSlotRepository;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SlotBlockServiceExceptionTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2024-01-15T09:00:00Z");
    private static final OffsetDateTime END = OffsetDateTime.parse("2024-01-15T11:00:00Z");

    @Test
    void createMapsOverlapViolationToConflict() {
        ParkingSlotRepository slotRepo = mock(ParkingSlotRepository.class);
        ParkingSlotBlockRepository blockRepo = fakeBlockRepository(new DataIntegrityViolationException("conflict", new SQLException("exclusion", "23P01")));
        InventoryConflictService conflictService = mock(InventoryConflictService.class);
        BlockMapper blockMapper = new BlockMapper();
        AuditService auditService = mock(AuditService.class);
        Clock clock = Clock.fixed(Instant.parse("2024-01-15T01:00:00Z"), ZoneOffset.UTC);

        ParkingLot lot = ParkingLot.builder().name("L").timezone("Asia/Jakarta").build();
        ParkingSlot slot = ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build();
        UUID slotId = slot.getId();

        when(slotRepo.findById(slotId)).thenReturn(Optional.of(slot));

        SlotBlockService service = new SlotBlockService(slotRepo, blockRepo, conflictService, blockMapper, auditService, clock);
        CreateBlockRequest request = new CreateBlockRequest(START, END, "r");
        assertThrows(ConflictException.class, () -> service.create(slotId, request, UUID.randomUUID(), START.minusHours(1)));
    }

    @Test
    void createRethrowsNonOverlapViolation() {
        ParkingSlotRepository slotRepo = mock(ParkingSlotRepository.class);
        ParkingSlotBlockRepository blockRepo = fakeBlockRepository(new DataIntegrityViolationException("other"));
        InventoryConflictService conflictService = mock(InventoryConflictService.class);
        BlockMapper blockMapper = new BlockMapper();
        AuditService auditService = mock(AuditService.class);
        Clock clock = Clock.fixed(Instant.parse("2024-01-15T01:00:00Z"), ZoneOffset.UTC);

        ParkingLot lot = ParkingLot.builder().name("L").timezone("Asia/Jakarta").build();
        ParkingSlot slot = ParkingSlot.builder().lot(lot).slotId("A-01").vehicleType("CAR").floor(1).build();
        UUID slotId = slot.getId();

        when(slotRepo.findById(slotId)).thenReturn(Optional.of(slot));

        SlotBlockService service = new SlotBlockService(slotRepo, blockRepo, conflictService, blockMapper, auditService, clock);
        CreateBlockRequest request = new CreateBlockRequest(START, END, "r");
        assertThrows(DataIntegrityViolationException.class, () -> service.create(slotId, request, UUID.randomUUID(), START.minusHours(1)));
    }

    private ParkingSlotBlockRepository fakeBlockRepository(RuntimeException toThrow) {
        return (ParkingSlotBlockRepository) Proxy.newProxyInstance(
                ParkingSlotBlockRepository.class.getClassLoader(),
                new Class[]{ParkingSlotBlockRepository.class},
                (proxy, method, args) -> {
                    if ("saveAndFlush".equals(method.getName())) {
                        throw toThrow;
                    }
                    if ("save".equals(method.getName())) {
                        throw toThrow;
                    }
                    Class<?> ret = method.getReturnType();
                    if (ret.equals(Optional.class)) return Optional.empty();
                    if (ret.equals(java.util.List.class)) return java.util.List.of();
                    if (ret.equals(void.class)) return null;
                    if (ret.equals(boolean.class)) return false;
                    if (ret.equals(long.class)) return 0L;
                    return null;
                });
    }
}
