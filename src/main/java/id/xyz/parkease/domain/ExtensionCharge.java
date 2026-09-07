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
@Table(name = "extension_charge")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ExtensionCharge {

    private static final int CURRENCY_MAX = 3;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @NotNull(message = "Reservation reference must not be null")
    @ManyToOne(optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, foreignKey = @ForeignKey(name = "fk_extension_charge_reservation"))
    private Reservation reservation;

    @NotNull(message = "Additional duration minutes must not be null")
    @Column(name = "additional_duration_minutes", nullable = false)
    private long additionalDurationMinutes;

    @NotNull(message = "Amount must not be null")
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Size(max = CURRENCY_MAX, message = "Currency exceeds maximum length")
    @Column(name = "currency", nullable = false, length = CURRENCY_MAX)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    @Builder.Default
    private ExtensionPaymentStatus paymentStatus = ExtensionPaymentStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public enum ExtensionPaymentStatus {
        PENDING,
        PAID,
        FAILED
    }

    public ExtensionCharge markPaid() {
        return toBuilder().paymentStatus(ExtensionPaymentStatus.PAID).build();
    }

    public ExtensionCharge markFailed() {
        return toBuilder().paymentStatus(ExtensionPaymentStatus.FAILED).build();
    }

    @PrePersist
    void applyDefaults() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (paymentStatus == null) {
            paymentStatus = ExtensionPaymentStatus.PENDING;
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
