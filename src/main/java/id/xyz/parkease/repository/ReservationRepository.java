package id.xyz.parkease.repository;

import id.xyz.parkease.domain.Reservation;
import id.xyz.parkease.domain.Reservation.Status;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findBySlot_IdAndStatus(UUID slotId, Status status);

    List<Reservation> findByStatus(Status status);

    @Query("SELECT r FROM Reservation r WHERE r.customerPlate = :plate AND r.status IN :activeStatuses ORDER BY r.plannedStart DESC")
    Optional<Reservation> findActiveByPlate(@Param("plate") String plate, @Param("activeStatuses") List<Status> activeStatuses);

    @Query("SELECT r FROM Reservation r WHERE r.plannedStart >= :from AND r.plannedEnd <= :to AND r.status = :status")
    List<Reservation> findByTimeWindow(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("status") Status status);

    @Query("SELECT r FROM Reservation r WHERE r.status = :status AND r.actualEnd IS NOT NULL ORDER BY r.actualEnd DESC")
    List<Reservation> findCompletedRecent(@Param("status") Status status, Pageable pageable);
}
