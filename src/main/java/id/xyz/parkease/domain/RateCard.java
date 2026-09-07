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
@Table(name = "rate_card")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RateCard {

    private static final int VEHICLE_MAX = 50;
    private static final int CURRENCY_MAX = 3;
    private static final int STATUS_MAX = 20;
    private static final int DEFAULT_VERSION = 1;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @NotNull(message = "Lot reference must not be null")
    @ManyToOne(optional = false)
    @JoinColumn(name = "lot_id", nullable = false, foreignKey = @ForeignKey(name = "fk_rate_card_lot"))
    private ParkingLot lot;

    @NotNull(message = "Vehicle type must not be null")
    @Size(max = VEHICLE_MAX, message = "Vehicle type exceeds maximum length")
    @Column(name = "vehicle_type", nullable = false, length = VEHICLE_MAX)
    private String vehicleType;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private int version = DEFAULT_VERSION;

    @NotNull(message = "Hourly rate must not be null")
    @Column(name = "hourly_rate", nullable = false, precision = 19, scale = 2)
    private BigDecimal hourlyRate;

    @NotNull(message = "Daily cap must not be null")
    @Column(name = "daily_cap", nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyCap;

    @NotNull(message = "Overnight surcharge must not be null")
    @Column(name = "overnight_surcharge", nullable = false, precision = 19, scale = 2)
    private BigDecimal overnightSurcharge;

    @Size(max = CURRENCY_MAX, message = "Currency exceeds maximum length")
    @Column(name = "currency", nullable = false, length = CURRENCY_MAX)
    @Builder.Default
    private String currency = "IDR";

    @NotNull(message = "Effective from must not be null")
    @Column(name = "effective_from", nullable = false)
    private OffsetDateTime effectiveFrom;

    @Column(name = "effective_to")
    private OffsetDateTime effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = STATUS_MAX)
    @Builder.Default
    private RateCardStatus status = RateCardStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public enum RateCardStatus {
        ACTIVE,
        RETIRED
    }

    public boolean isEffectiveAt(OffsetDateTime instant) {
        boolean afterStart = !instant.isBefore(effectiveFrom);
        boolean beforeEnd = effectiveTo == null || instant.isBefore(effectiveTo);
        return afterStart && beforeEnd;
    }

    @PrePersist
    void applyDefaults() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = RateCardStatus.ACTIVE;
        }
        if (currency == null) {
            currency = "IDR";
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
