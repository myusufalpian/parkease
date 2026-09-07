package id.xyz.parkease.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_event")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    private static final int EVENT_TYPE_MAX = 50;
    private static final int STATUS_MAX = 20;
    private static final int DEFAULT_ATTEMPTS = 0;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @NotNull(message = "Event type must not be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = EVENT_TYPE_MAX)
    private EventType eventType;

    @NotNull(message = "Aggregate id must not be null")
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @NotNull(message = "Payload must not be null")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "JSONB", nullable = false)
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = STATUS_MAX)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = DEFAULT_ATTEMPTS;

    @Size(max = 500, message = "Last error exceeds maximum length")
    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    public enum EventType {
        PAYMENT_CAPTURE,
        REFUND,
        EXTENSION_PAYMENT
    }

    public enum OutboxStatus {
        PENDING,
        PROCESSED,
        FAILED
    }

    public OutboxEvent markProcessed(OffsetDateTime processedAt) {
        return toBuilder().status(OutboxStatus.PROCESSED).processedAt(processedAt).build();
    }

    public OutboxEvent markFailed(String error, OffsetDateTime processedAt) {
        return toBuilder()
                .status(OutboxStatus.FAILED)
                .attempts(attempts + 1)
                .lastError(error)
                .processedAt(processedAt)
                .build();
    }

    @PrePersist
    void applyDefaults() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = OutboxStatus.PENDING;
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
