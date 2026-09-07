package id.xyz.parkease.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "customer_account", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CustomerAccount {

    private static final int USERNAME_MAX = 100;
    private static final int PASSWORD_HASH_MAX = 255;
    private static final int STATUS_MAX = 20;
    private static final int ROLE_MAX = 20;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @NotBlank(message = "Username must not be blank")
    @Size(max = USERNAME_MAX, message = "Username exceeds maximum length")
    @Column(name = "username", nullable = false, length = USERNAME_MAX)
    private String username;

    @NotBlank(message = "Password hash must not be blank")
    @Size(max = PASSWORD_HASH_MAX, message = "Password hash exceeds maximum length")
    @Column(name = "password_hash", nullable = false, length = PASSWORD_HASH_MAX)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = ROLE_MAX)
    @Builder.Default
    private Role role = Role.CUSTOMER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = STATUS_MAX)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public enum Role {
        CUSTOMER,
        OPERATOR,
        ADMIN
    }

    public enum AccountStatus {
        ACTIVE,
        DISABLED
    }

    @PrePersist
    void applyDefaults() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (role == null) {
            role = Role.CUSTOMER;
        }
        if (status == null) {
            status = AccountStatus.ACTIVE;
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
    }
}
