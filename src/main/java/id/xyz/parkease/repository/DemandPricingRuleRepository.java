package id.xyz.parkease.repository;

import id.xyz.parkease.domain.DemandPricingRule;
import id.xyz.parkease.domain.DemandPricingRule.RuleStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DemandPricingRuleRepository extends JpaRepository<DemandPricingRule, UUID> {
    List<DemandPricingRule> findByLot_IdAndStatusOrderByOccupancyThresholdDesc(UUID lotId, RuleStatus status);
}
