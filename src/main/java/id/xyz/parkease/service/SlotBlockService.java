package id.xyz.parkease.service;

import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.domain.ParkingSlotBlock;
import id.xyz.parkease.domain.ParkingSlotBlock.BlockStatus;
import id.xyz.parkease.dto.BlockResponse;
import id.xyz.parkease.dto.CreateBlockRequest;
import id.xyz.parkease.exception.BusinessValidationException;
import id.xyz.parkease.exception.ConflictException;
import id.xyz.parkease.exception.ResourceNotFoundException;
import id.xyz.parkease.exception.SqlState;
import id.xyz.parkease.mapper.BlockMapper;
import id.xyz.parkease.repository.ParkingSlotBlockRepository;
import id.xyz.parkease.repository.ParkingSlotRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SlotBlockService {

    private final ParkingSlotRepository parkingSlotRepository;
    private final ParkingSlotBlockRepository blockRepository;
    private final InventoryConflictService inventoryConflictService;
    private final BlockMapper blockMapper;
    private final AuditService auditService;
    private final Clock clock;

    public SlotBlockService(
            ParkingSlotRepository parkingSlotRepository,
            ParkingSlotBlockRepository blockRepository,
            InventoryConflictService inventoryConflictService,
            BlockMapper blockMapper,
            AuditService auditService,
            Clock clock) {
        this.parkingSlotRepository = Objects.requireNonNull(parkingSlotRepository);
        this.blockRepository = Objects.requireNonNull(blockRepository);
        this.inventoryConflictService = Objects.requireNonNull(inventoryConflictService);
        this.blockMapper = Objects.requireNonNull(blockMapper);
        this.auditService = Objects.requireNonNull(auditService);
        this.clock = Objects.requireNonNull(clock);
    }

    public BlockResponse create(UUID slotId, CreateBlockRequest request, UUID actorId) {
        return create(slotId, request, actorId, currentTime());
    }

    public BlockResponse create(UUID slotId, CreateBlockRequest request, UUID actorId, OffsetDateTime now) {
        Objects.requireNonNull(slotId, "slotId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        validateInterval(request.blockedStart(), request.blockedEnd());
        ParkingSlot slot = inventoryConflictService.requireNoConflictForBlock(slotId, request.blockedStart(), request.blockedEnd());
        String sanitizedReason = sanitize(request.reason());

        ParkingSlotBlock block = ParkingSlotBlock.builder()
                .slot(slot)
                .blockedStart(request.blockedStart())
                .blockedEnd(request.blockedEnd())
                .reason(sanitizedReason)
                .status(BlockStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            ParkingSlotBlock saved = blockRepository.saveAndFlush(block);
            auditService.record(actorId, "SLOT_BLOCK_CREATE",
                    "lot:" + slot.getLot().getId() + ";block:" + saved.getId(),
                    sanitizedReason, null, "ACTIVE:" + request.blockedStart() + "->" + request.blockedEnd());
            return blockMapper.toResponse(saved);
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            if (SqlState.isOverlapOrDeadlock(exception)) {
                throw new ConflictException("parking slot is already blocked for the requested time window");
            }
            throw exception;
        }
    }

    public List<BlockResponse> list(UUID slotId) {
        return list(slotId, currentTime());
    }

    @Transactional
    public List<BlockResponse> list(UUID slotId, OffsetDateTime now) {
        parkingSlotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("parking slot was not found"));
        List<ParkingSlotBlock> blocks = blockRepository.findBySlot_IdOrderByBlockedStartAsc(slotId);
        return blocks.stream()
                .map(block -> refreshExpiry(block, now))
                .map(blockMapper::toResponse)
                .toList();
    }

    public BlockResponse delete(UUID blockId, UUID actorId) {
        return delete(blockId, actorId, currentTime());
    }

    public BlockResponse delete(UUID blockId, UUID actorId, OffsetDateTime now) {
        ParkingSlotBlock block = blockRepository.findById(blockId)
                .orElseThrow(() -> new ResourceNotFoundException("slot block was not found"));
        ParkingSlotBlock current = refreshExpiry(block, now);
        if (current.getStatus() != BlockStatus.ACTIVE) {
            return blockMapper.toResponse(current);
        }
        String sanitizedReason = sanitize(current.getReason());
        String lotId = current.getSlot().getLot().getId().toString();
        if (current.isExpiredAt(now)) {
            ParkingSlotBlock expired = blockRepository.save(current.toBuilder().status(BlockStatus.EXPIRED).updatedAt(now).build());
            auditService.record(actorId, "SLOT_BLOCK_EXPIRED", "lot:" + lotId + ";block:" + blockId, sanitizedReason, "ACTIVE", "EXPIRED");
            return blockMapper.toResponse(expired);
        }
        ParkingSlotBlock cancelled = blockRepository.save(current.toBuilder().status(BlockStatus.CANCELLED).updatedAt(now).build());
        auditService.record(actorId, "SLOT_BLOCK_CANCEL", "lot:" + lotId + ";block:" + blockId, sanitizedReason, "ACTIVE", "CANCELLED");
        return blockMapper.toResponse(cancelled);
    }

    private ParkingSlotBlock refreshExpiry(ParkingSlotBlock block, OffsetDateTime now) {
        if (block.getStatus() == BlockStatus.ACTIVE && block.isExpiredAt(now)) {
          return blockRepository.save(block.toBuilder().status(BlockStatus.EXPIRED).updatedAt(now).build());
        }
        return block;
    }

    private void validateInterval(OffsetDateTime start, OffsetDateTime end) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new BusinessValidationException("blocked end must be after blocked start");
        }
    }

    private String sanitize(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("[\\r\\n\\t]", "_").trim();
    }

    private OffsetDateTime currentTime() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
