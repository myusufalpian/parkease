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
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "refund", uniqueConstraints = @UniqueConstraint(columnNames = "payment_transaction_id"))
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Refund {

    private static final int STATUS_MAX = 20;
    private static final int PROVIDER_REFERENCE_MAX = 100;
    private static final int PAYMENT_TRANSACTION_ID_MAX = 100;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @NotNull(message = "Invoice reference must not be null")
    @ManyToOne(optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, foreignKey = @ForeignKey(name = "fk_refund_invoice"))
    private ParkingInvoice invoice;

    @NotNull(message = "Payment transaction id must not be null")
    @Size(max = PAYMENT_TRANSACTION_ID_MAX, message = "Payment transaction id exceeds maximum length")
    @Column(name = "payment_transaction_id", nullable = false, length = PAYMENT_TRANSACTION_ID_MAX)
    private String paymentTransactionId;

    @NotNull(message = "Fee amount must not be null")
    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal feeAmount;

    @NotNull(message = "Refund amount must not be null")
    @Column(name = "refund_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal refundAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = STATUS_MAX)
    @Builder.Default
    private RefundStatus status = RefundStatus.PENDING;

    @Size(max = PROVIDER_REFERENCE_MAX, message = "Provider reference exceeds maximum length")
    @Column(name = "provider_reference", length = PROVIDER_REFERENCE_MAX)
    private String providerReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public enum RefundStatus {
        PENDING,
        PROCESSING,
        SUCCEEDED,
        FAILED
    }

    public Refund markProcessing(OffsetDateTime updatedAt) {
        return toBuilder().status(RefundStatus.PROCESSING).updatedAt(updatedAt).build();
    }

    public Refund markSucceeded(String providerReference, OffsetDateTime updatedAt) {
        Objects.requireNonNull(providerReference, "providerReference must not be null");
        return toBuilder().status(RefundStatus.SUCCEEDED).providerReference(providerReference).updatedAt(updatedAt).build();
    }

    public Refund markFailed(OffsetDateTime updatedAt) {
        return toBuilder().status(RefundStatus.FAILED).updatedAt(updatedAt).build();
    }

    @PrePersist
    void applyDefaults() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = RefundStatus.PENDING;
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
    }
}
