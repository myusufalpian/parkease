package id.xyz.parkease.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pricing_promotion")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PricingPromotion {

    private static final int CODE_MAX = 50;
    private static final int TYPE_MAX = 50;
    private static final int DISCOUNT_TYPE_MAX = 20;
    private static final int STATUS_MAX = 20;
    private static final int DEFAULT_USAGE_COUNT = 0;

    @Id
    @Column(name = "code", nullable = false, length = CODE_MAX, unique = true)
    @Builder.Default
    private String code = UUID.randomUUID().toString();

    @ManyToOne
    @JoinColumn(name = "lot_id", foreignKey = @ForeignKey(name = "fk_promo_lot"))
    private ParkingLot lot;

    @Size(max = TYPE_MAX, message = "Vehicle type exceeds maximum length")
    @Column(name = "vehicle_type", length = TYPE_MAX)
    private String vehicleType;

    @Size(max = TYPE_MAX, message = "Customer type exceeds maximum length")
    @Column(name = "customer_type", length = TYPE_MAX)
    private String customerType;

    @NotNull(message = "Effective from must not be null")
    @Column(name = "effective_from", nullable = false)
    private OffsetDateTime effectiveFrom;

    @NotNull(message = "Effective to must not be null")
    @Column(name = "effective_to", nullable = false)
    private OffsetDateTime effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", length = DISCOUNT_TYPE_MAX, nullable = false)
    private DiscountType discountType;

    @NotNull(message = "Discount value must not be null")
    @Column(name = "discount_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "usage_count", nullable = false)
    @Builder.Default
    private int usageCount = DEFAULT_USAGE_COUNT;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = STATUS_MAX, nullable = false)
    @Builder.Default
    private PromoStatus status = PromoStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public enum DiscountType {
        PERCENTAGE,
        FIXED
    }

    public enum PromoStatus { ACTIVE, EXPIRED }

    @PrePersist
    void applyDefaults() {
        if (code == null) {
            code = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = PromoStatus.ACTIVE;
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
    }

    public PricingPromotion consume() {
        return toBuilder().usageCount(usageCount + 1).updatedAt(OffsetDateTime.now()).build();
    }

}
