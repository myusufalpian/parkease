package id.xyz.parkease.repository;

import id.xyz.parkease.domain.ParkingSlot;
import id.xyz.parkease.domain.ParkingSlot.SlotStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ParkingSlotRepository extends JpaRepository<ParkingSlot, UUID> {

    List<ParkingSlot> findByLot_IdOrderByFloorAscSlotIdAsc(UUID lotId);

    @Query("SELECT ps FROM ParkingSlot ps WHERE ps.lot.id = :lotId AND ps.vehicleType = :vehicleType AND ps.status = :status")
    Optional<ParkingSlot> findAvailableSlotByLotAndVehicle(
            @Param("lotId") UUID lotId,
            @Param("vehicleType") String vehicleType,
            @Param("status") SlotStatus status);

    long countByLot_IdAndVehicleType(UUID lotId, String vehicleType);
}
