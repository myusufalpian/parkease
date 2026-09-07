package id.xyz.parkease.repository;

import id.xyz.parkease.domain.RateCard;
import id.xyz.parkease.domain.RateCard.RateCardStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RateCardRepository extends JpaRepository<RateCard, UUID> {

    List<RateCard> findByLot_IdAndVehicleTypeAndStatusOrderByVersionDesc(
            UUID lotId, String vehicleType, RateCardStatus status);
}
