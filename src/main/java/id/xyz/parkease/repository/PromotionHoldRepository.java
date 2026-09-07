package id.xyz.parkease.repository;

import id.xyz.parkease.domain.PromotionHold;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromotionHoldRepository extends JpaRepository<PromotionHold, UUID> {
    Optional<PromotionHold> findByReservation_Id(UUID reservationId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PromotionHold h SET h.status = :target WHERE h.reservation.id = :reservationId AND h.status = :expected")
    int transition(@Param("reservationId") UUID reservationId,
                   @Param("expected") PromotionHold.HoldStatus expected,
                   @Param("target") PromotionHold.HoldStatus target);
}
