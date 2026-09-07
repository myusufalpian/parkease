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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "parking_slot",
        uniqueConstraints = @UniqueConstraint(columnNames = {"lot_id", "slot_id"}))
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ParkingSlot {

    private static final int SLOT_ID_MAX = 100;
    private static final int VEHICLE_MAX = 50;
    private static final int STATUS_MAX = 20;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @NotNull(message = "Lot reference must not be null")
    @ManyToOne(optional = false)
    @JoinColumn(name = "lot_id", nullable = false, foreignKey = @ForeignKey(name = "fk_slot_lot"))
    private ParkingLot lot;

    @NotNull(message = "Slot ID must not be null")
    @Size(max = SLOT_ID_MAX, message = "Slot ID exceeds maximum length")
    @Column(name = "slot_id", nullable = false, length = SLOT_ID_MAX)
    private String slotId;

    @NotNull(message = "Vehicle type must not be null")
    @Size(max = VEHICLE_MAX, message = "Vehicle type exceeds maximum length")
    @Column(name = "vehicle_type", nullable = false, length = VEHICLE_MAX)
    private String vehicleType;

    @NotNull(message = "Floor must not be null")
    @Column(name = "floor", nullable = false)
    private Integer floor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = STATUS_MAX, nullable = false)
    @Builder.Default
    private SlotStatus status = SlotStatus.AVAILABLE;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public enum SlotStatus {
        AVAILABLE,
        RESERVED,
        OCCUPIED,
        MAINTENANCE
    }

    @PrePersist
    void applyDefaults() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = SlotStatus.AVAILABLE;
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
        Objects.requireNonNull(lot, "lot must not be null");
    }

    public ParkingSlot markReserved() {
        return toBuilder().status(SlotStatus.RESERVED).updatedAt(OffsetDateTime.now()).build();
    }

    public ParkingSlot markOccupied() {
        return toBuilder().status(SlotStatus.OCCUPIED).updatedAt(OffsetDateTime.now()).build();
    }

    public ParkingSlot markAvailable() {
        return toBuilder().status(SlotStatus.AVAILABLE).updatedAt(OffsetDateTime.now()).build();
    }
}
