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
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reservation")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {

    private static final int PLATE_MAX = 20;
    private static final int STATUS_MAX = 20;
    private static final int REASON_MAX = 100;
    private static final int PROMO_MAX = 50;
    private static final int DEFAULT_RATE_CARD_VERSION = 1;
    private static final String DEFAULT_PRICING_SNAPSHOT = "{}";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @NotNull(message = "Slot reference must not be null")
    @ManyToOne(optional = false)
    @JoinColumn(name = "slot_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reservation_slot"))
    private ParkingSlot slot;

    @Size(max = PLATE_MAX, message = "License plate exceeds maximum length")
    @Column(name = "customer_plate", length = PLATE_MAX)
    private String customerPlate;

    @NotNull(message = "Planned start must not be null")
    @Column(name = "planned_start", nullable = false)
    private OffsetDateTime plannedStart;

    @NotNull(message = "Planned end must not be null")
    @Column(name = "planned_end", nullable = false)
    private OffsetDateTime plannedEnd;

    @Column(name = "actual_start")
    private OffsetDateTime actualStart;

    @Column(name = "actual_end")
    private OffsetDateTime actualEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = STATUS_MAX)
    @Builder.Default
    private Status status = Status.PENDING;

    @Size(max = REASON_MAX, message = "Cancellation reason exceeds maximum length")
    @Column(name = "cancellation_reason", length = REASON_MAX)
    private String cancellationReason;

    @Column(name = "late_cancellation", nullable = false)
    @Builder.Default
    private boolean lateCancellation = false;

    @Size(max = PROMO_MAX, message = "Promo code exceeds maximum length")
    @Column(name = "promo_code", length = PROMO_MAX)
    private String promoCode;

    @Column(name = "rate_card_version", nullable = false)
    @Builder.Default
    private int rateCardVersion = DEFAULT_RATE_CARD_VERSION;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pricing_snapshot", columnDefinition = "JSONB", nullable = false)
    @Builder.Default
    private String pricingSnapshotJson = DEFAULT_PRICING_SNAPSHOT;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @AssertTrue(message = "Planned end must be after planned start")
    public boolean isPlannedWindowValid() {
        return plannedStart == null || plannedEnd == null || plannedStart.isBefore(plannedEnd);
    }

    public enum Status {
        PENDING,
        ACTIVE,
        COMPLETED,
        NO_SHOW,
        CANCELLED
    }

    public Reservation checkIn(OffsetDateTime actualStart) {
        Objects.requireNonNull(actualStart, "actualStart must not be null");
        return toBuilder().status(Status.ACTIVE).actualStart(actualStart).updatedAt(OffsetDateTime.now()).build();
    }

    public Reservation markNoShow(OffsetDateTime actualStart) {
        Objects.requireNonNull(actualStart, "actualStart must not be null");
        return toBuilder().status(Status.NO_SHOW).actualStart(actualStart).updatedAt(OffsetDateTime.now()).build();
    }

    public Reservation complete(OffsetDateTime actualEnd) {
        Objects.requireNonNull(actualEnd, "actualEnd must not be null");
        return toBuilder().status(Status.COMPLETED).actualEnd(actualEnd).updatedAt(OffsetDateTime.now()).build();
    }

    public Reservation cancel(String reason, boolean late) {
        return toBuilder().status(Status.CANCELLED).cancellationReason(reason).lateCancellation(late).updatedAt(OffsetDateTime.now()).build();
    }

    @PrePersist
    void applyDefaults() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = Status.PENDING;
        }
        if (pricingSnapshotJson == null) {
            pricingSnapshotJson = DEFAULT_PRICING_SNAPSHOT;
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
    }
}
