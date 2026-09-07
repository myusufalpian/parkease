package id.xyz.parkease.repository;

import id.xyz.parkease.domain.ExtensionCharge;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExtensionChargeRepository extends JpaRepository<ExtensionCharge, UUID> {

    List<ExtensionCharge> findByReservation_Id(UUID reservationId);
}
