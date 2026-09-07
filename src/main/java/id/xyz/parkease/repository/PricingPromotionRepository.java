package id.xyz.parkease.repository;

import id.xyz.parkease.domain.PricingPromotion;
import id.xyz.parkease.domain.PricingPromotion.PromoStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PricingPromotionRepository extends JpaRepository<PricingPromotion, String> {

    Optional<PricingPromotion> findByCode(String code);

    List<PricingPromotion> findAllByStatus(PromoStatus status);

    @Query("SELECT p FROM PricingPromotion p WHERE p.effectiveFrom <= :now AND p.effectiveTo >= :now AND p.status = :status AND (p.lot.id IS NULL OR p.lot.id = :lotId) AND (p.vehicleType IS NULL OR p.vehicleType = :vehicleType) AND (p.customerType IS NULL OR p.customerType = :customerType)")
    Optional<PricingPromotion> findActiveByScope(
            @Param("now") OffsetDateTime now,
            @Param("status") PromoStatus status,
            @Param("lotId") UUID lotId,
            @Param("vehicleType") String vehicleType,
            @Param("customerType") String customerType);

    @Query("SELECT p FROM PricingPromotion p WHERE p.usageCount < p.usageLimit AND p.usageLimit IS NOT NULL")
    List<PricingPromotion> findWithRemainingUsage();
}
