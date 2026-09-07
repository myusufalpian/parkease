package id.xyz.parkease.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
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
@Table(name = "parking_lot")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ParkingLot {

    private static final int NAME_MAX = 255;
    private static final int LOCATION_MAX = 500;
    private static final int TIMEZONE_MAX = 50;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @NotBlank(message = "Lot name must not be blank")
    @Size(max = NAME_MAX, message = "Lot name exceeds maximum length")
    @Column(name = "name", nullable = false, length = NAME_MAX)
    private String name;

    @Size(max = LOCATION_MAX, message = "Location exceeds maximum length")
    @Column(name = "location", length = LOCATION_MAX)
    private String location;

    @NotBlank(message = "Lot timezone must not be blank")
    @Size(max = TIMEZONE_MAX, message = "Lot timezone exceeds maximum length")
    @Column(name = "timezone", nullable = false, length = TIMEZONE_MAX)
    private String timezone;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "operating_hours", columnDefinition = "JSONB")
    private String operatingHours;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PrePersist
    void applyDefaults() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
    }
}
