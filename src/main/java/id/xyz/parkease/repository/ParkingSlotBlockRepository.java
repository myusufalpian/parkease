package id.xyz.parkease.repository;

import id.xyz.parkease.domain.ParkingSlotBlock;
import id.xyz.parkease.domain.ParkingSlotBlock.BlockStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ParkingSlotBlockRepository extends JpaRepository<ParkingSlotBlock, UUID> {

    List<ParkingSlotBlock> findBySlot_IdAndStatus(UUID slotId, BlockStatus status);

    List<ParkingSlotBlock> findBySlot_IdOrderByBlockedStartAsc(UUID slotId);

    @Query("SELECT b FROM ParkingSlotBlock b WHERE b.slot.id = :slotId AND b.status = :status AND b.blockedStart < :end AND b.blockedEnd > :start")
    List<ParkingSlotBlock> findActiveOverlapping(
            @Param("slotId") UUID slotId,
            @Param("status") BlockStatus status,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);
}
