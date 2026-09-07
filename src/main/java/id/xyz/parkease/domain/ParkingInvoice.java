package id.xyz.parkease.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "parking_invoice", uniqueConstraints = @UniqueConstraint(columnNames = "reservation_id"))
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ParkingInvoice {

    private static final int CURRENCY_MAX = 3;
    private static final int STATUS_MAX = 20;
    private static final String DEFAULT_PRICING_SNAPSHOT = "{}";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @NotNull(message = "Reservation reference must not be null")
    @OneToOne(optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, foreignKey = @ForeignKey(name = "fk_parking_invoice_reservation"))
    private Reservation reservation;

    @NotNull(message = "Duration minutes must not be null")
    @Column(name = "duration_minutes", nullable = false)
    private long durationMinutes;

    @NotNull(message = "Subtotal must not be null")
    @Column(name = "subtotal", nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    @NotNull(message = "Discount amount must not be null")
    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @NotNull(message = "Total must not be null")
    @Column(name = "total", nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Size(max = CURRENCY_MAX, message = "Currency exceeds maximum length")
    @Column(name = "currency", nullable = false, length = CURRENCY_MAX)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = STATUS_MAX)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PAYMENT_PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pricing_snapshot", columnDefinition = "JSONB", nullable = false)
    @Builder.Default
    private String pricingSnapshotJson = DEFAULT_PRICING_SNAPSHOT;

    @Column(name = "generated_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime generatedAt = OffsetDateTime.now();

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    public enum PaymentStatus {
        PAYMENT_PENDING,
        PAID,
        FAILED,
        EXPIRED
    }

    public ParkingInvoice markPaid(OffsetDateTime paidAt) {
        Objects.requireNonNull(paidAt, "paidAt must not be null");
        return toBuilder().paymentStatus(PaymentStatus.PAID).paidAt(paidAt).build();
    }

    public ParkingInvoice markFailed() {
        return toBuilder().paymentStatus(PaymentStatus.FAILED).build();
    }

    public ParkingInvoice markExpired() {
        return toBuilder().paymentStatus(PaymentStatus.EXPIRED).build();
    }

    @PrePersist
    void applyDefaults() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (discountAmount == null) {
            discountAmount = BigDecimal.ZERO;
        }
        if (paymentStatus == null) {
            paymentStatus = PaymentStatus.PAYMENT_PENDING;
        }
        if (pricingSnapshotJson == null) {
            pricingSnapshotJson = DEFAULT_PRICING_SNAPSHOT;
        }
        if (generatedAt == null) {
            generatedAt = OffsetDateTime.now();
        }
    }
}
