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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "billing_operation",
        uniqueConstraints = {
            @UniqueConstraint(columnNames = "idempotency_key"),
            @UniqueConstraint(columnNames = {"reservation_id", "operation_type"})
        })
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class BillingOperation {

    private static final int IDEMPOTENCY_KEY_MAX = 100;
    private static final int STATUS_MAX = 20;
    private static final String DEFAULT_RESPONSE_SNAPSHOT = "{}";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @NotNull(message = "Reservation reference must not be null")
    @ManyToOne(optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, foreignKey = @ForeignKey(name = "fk_billing_operation_reservation"))
    private Reservation reservation;

    @NotNull(message = "Operation type must not be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = STATUS_MAX)
    private OperationType operationType;

    @NotNull(message = "Idempotency key must not be null")
    @Size(max = IDEMPOTENCY_KEY_MAX, message = "Idempotency key exceeds maximum length")
    @Column(name = "idempotency_key", nullable = false, length = IDEMPOTENCY_KEY_MAX)
    private String idempotencyKey;

    @NotNull(message = "Operation status must not be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = STATUS_MAX)
    @Builder.Default
    private OperationStatus status = OperationStatus.COMPLETED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_snapshot", columnDefinition = "JSONB", nullable = false)
    @Builder.Default
    private String responseSnapshotJson = DEFAULT_RESPONSE_SNAPSHOT;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public enum OperationType {
        CHECKOUT,
        CANCELLATION,
        PAYMENT,
        REFUND
    }

    public enum OperationStatus {
        COMPLETED,
        FAILED
    }

    @PrePersist
    void applyDefaults() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = OperationStatus.COMPLETED;
        }
        if (responseSnapshotJson == null) {
            responseSnapshotJson = DEFAULT_RESPONSE_SNAPSHOT;
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
