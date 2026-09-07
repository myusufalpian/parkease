package id.xyz.parkease.repository;

import id.xyz.parkease.domain.ReservationOwner;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReservationOwnerRepository extends JpaRepository<ReservationOwner, UUID> {

    Optional<ReservationOwner> findByReservation_Id(UUID reservationId);
}
