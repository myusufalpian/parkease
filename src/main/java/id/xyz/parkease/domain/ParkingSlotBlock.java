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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "parking_slot_block")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ParkingSlotBlock {

    private static final int REASON_MAX = 255;
    private static final int STATUS_MAX = 20;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "slot_id", nullable = false, foreignKey = @ForeignKey(name = "fk_block_slot"))
    private ParkingSlot slot;

    @NotNull
    @Column(name = "blocked_start", nullable = false)
    private OffsetDateTime blockedStart;

    @NotNull
    @Column(name = "blocked_end", nullable = false)
    private OffsetDateTime blockedEnd;

    @NotNull
    @Size(max = REASON_MAX)
    @Column(name = "reason", nullable = false, length = REASON_MAX)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = STATUS_MAX)
    @Builder.Default
    private BlockStatus status = BlockStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public enum BlockStatus {
        ACTIVE,
        CANCELLED,
        EXPIRED
    }

    public boolean isActiveAt(OffsetDateTime now) {
        return status == BlockStatus.ACTIVE && blockedEnd.isAfter(now);
    }

    public boolean isExpiredAt(OffsetDateTime now) {
        return status == BlockStatus.ACTIVE && !blockedEnd.isAfter(now);
    }

    @PrePersist
    void applyDefaults() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = BlockStatus.ACTIVE;
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
    }

    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }
}
