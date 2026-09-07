package id.xyz.parkease.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reservation_owner")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ReservationOwner {

    @Id
    @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;

    @NotNull(message = "Reservation reference must not be null")
    @OneToOne(optional = false)
    @MapsId
    @JoinColumn(name = "reservation_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reservation_owner_reservation"))
    private Reservation reservation;

    @NotNull(message = "Customer account reference must not be null")
    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_account_id", nullable = false, foreignKey = @ForeignKey(name = "fk_reservation_owner_customer_account"))
    private CustomerAccount customerAccount;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @PrePersist
    void applyDefaults() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
