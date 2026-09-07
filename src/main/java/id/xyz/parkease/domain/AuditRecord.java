package id.xyz.parkease.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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
@Table(name = "audit_record")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AuditRecord {

    private static final int ACTION_MAX = 50;
    private static final int TARGET_MAX = 100;
    private static final int REASON_MAX = 255;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "actor_id")
    private UUID actorId;

    @NotNull(message = "Action must not be null")
    @Size(max = ACTION_MAX, message = "Action exceeds maximum length")
    @Column(name = "action", nullable = false, length = ACTION_MAX)
    private String action;

    @Size(max = TARGET_MAX, message = "Target exceeds maximum length")
    @Column(name = "target", length = TARGET_MAX)
    private String target;

    @Size(max = REASON_MAX, message = "Reason exceeds maximum length")
    @Column(name = "reason", length = REASON_MAX)
    private String reason;

    @Size(max = REASON_MAX, message = "Before state exceeds maximum length")
    @Column(name = "before_state", length = REASON_MAX)
    private String beforeState;

    @Size(max = REASON_MAX, message = "After state exceeds maximum length")
    @Column(name = "after_state", length = REASON_MAX)
    private String afterState;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @NotNull(message = "Occurred at must not be null")
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    @PrePersist
    void applyDefaults() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (occurredAt == null) {
            occurredAt = OffsetDateTime.now();
        }
    }
}
