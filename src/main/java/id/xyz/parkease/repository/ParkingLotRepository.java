package id.xyz.parkease.repository;

import id.xyz.parkease.domain.ParkingLot;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParkingLotRepository extends JpaRepository<ParkingLot, UUID> {

    Optional<ParkingLot> findByName(String name);

    Optional<ParkingLot> findByTimezone(String timezone);
}
