package id.xyz.parkease.repository;

import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.domain.ParkingSlot.SlotStatus;
import id.xyz.parkease.domain.Reservation.Status;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ParkingSlotRepository extends JpaRepository<ParkingSlot, UUID> {

    List<ParkingSlot> findByLot_IdOrderByFloorAscSlotIdAsc(UUID lotId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ps FROM ParkingSlot ps WHERE ps.id = :id")
    Optional<ParkingSlot> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT ps FROM ParkingSlot ps WHERE ps.lot.id = :lotId AND ps.vehicleType = :vehicleType AND ps.status = :status")
    Optional<ParkingSlot> findAvailableSlotByLotAndVehicle(
            @Param("lotId") UUID lotId,
            @Param("vehicleType") String vehicleType,
            @Param("status") SlotStatus status);

    long countByLot_IdAndVehicleType(UUID lotId, String vehicleType);

    @Query("""
            SELECT ps FROM ParkingSlot ps
            WHERE ps.lot.id = :lotId
              AND ps.status <> :maintenanceStatus
              AND (:vehicleType IS NULL OR ps.vehicleType = :vehicleType)
              AND NOT EXISTS (
                  SELECT 1 FROM Reservation r
                  WHERE r.slot.id = ps.id
                    AND r.status IN :activeStatuses
                    AND r.plannedStart < :end
                    AND r.plannedEnd > :start
              )
              AND NOT EXISTS (
                  SELECT 1 FROM ParkingSlotBlock b
                  WHERE b.slot.id = ps.id
                    AND b.status = :activeBlockStatus
                    AND b.blockedStart < :end
                    AND b.blockedEnd > :start
              )
            ORDER BY ps.floor ASC, ps.slotId ASC
            """)
    List<ParkingSlot> findAvailableSlots(
            @Param("lotId") UUID lotId,
            @Param("vehicleType") String vehicleType,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("activeStatuses") List<Status> activeStatuses,
            @Param("maintenanceStatus") SlotStatus maintenanceStatus,
            @Param("activeBlockStatus") id.xyz.parkease.domain.ParkingSlotBlock.BlockStatus activeBlockStatus);
}
