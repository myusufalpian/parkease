package id.xyz.parkease.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "demand_pricing_rule")
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class DemandPricingRule {
    @Id @Builder.Default private UUID id = UUID.randomUUID();
    @ManyToOne(optional = false) @JoinColumn(name = "lot_id", nullable = false) private ParkingLot lot;
    @Column(name = "vehicle_type", length = 50) private String vehicleType;
    @NotNull @Column(name = "effective_from", nullable = false) private OffsetDateTime effectiveFrom;
    @NotNull @Column(name = "effective_to", nullable = false) private OffsetDateTime effectiveTo;
    @NotNull @DecimalMin("0.0") @DecimalMax("1.0") @Column(name = "occupancy_threshold", nullable = false, precision = 5, scale = 4) private BigDecimal occupancyThreshold;
    @NotNull @DecimalMin(value = "0.0001") @Column(name = "multiplier", nullable = false, precision = 8, scale = 4) private BigDecimal multiplier;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 20)
    @Builder.Default private RuleStatus status = RuleStatus.ACTIVE;
    public enum RuleStatus { ACTIVE, RETIRED }

    @AssertTrue(message = "effective to must be after effective from")
    public boolean isEffectiveWindowValid() {
        return effectiveFrom == null || effectiveTo == null || effectiveFrom.isBefore(effectiveTo);
    }
}
