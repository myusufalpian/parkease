package id.xyz.parkease.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "session")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Session {

    private static final int TOKEN_HASH_MAX = 128;
    private static final int REVOKED_REASON_MAX = 30;
    private static final int USER_AGENT_MAX = 500;
    private static final int FAMILY_MAX = 100;
    private static final int IP_ADDRESS_MAX = 64;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @NotNull(message = "Session owner must not be null")
    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_account_id", nullable = false, foreignKey = @ForeignKey(name = "fk_session_customer_account"))
    private CustomerAccount customerAccount;

    @NotNull(message = "Refresh token hash must not be null")
    @Size(max = TOKEN_HASH_MAX, message = "Refresh token hash exceeds maximum length")
    @Column(name = "refresh_token_hash", nullable = false, length = TOKEN_HASH_MAX)
    private String refreshTokenHash;

    @NotNull(message = "Issued at must not be null")
    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @NotNull(message = "Expires at must not be null")
    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Size(max = REVOKED_REASON_MAX, message = "Revoked reason exceeds maximum length")
    @Column(name = "revoked_reason", length = REVOKED_REASON_MAX)
    private String revokedReason;

    @Size(max = USER_AGENT_MAX, message = "User agent exceeds maximum length")
    @Column(name = "user_agent", length = USER_AGENT_MAX)
    private String userAgent;

    @Size(max = FAMILY_MAX, message = "Device family exceeds maximum length")
    @Column(name = "device_family", length = FAMILY_MAX)
    private String deviceFamily;

    @Size(max = FAMILY_MAX, message = "OS family exceeds maximum length")
    @Column(name = "os_family", length = FAMILY_MAX)
    private String osFamily;

    @Size(max = FAMILY_MAX, message = "Browser family exceeds maximum length")
    @Column(name = "browser_family", length = FAMILY_MAX)
    private String browserFamily;

    @Size(max = IP_ADDRESS_MAX, message = "IP address exceeds maximum length")
    @Column(name = "ip_address", length = IP_ADDRESS_MAX)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public enum RevokeReason {
        ROTATED,
        LOGOUT,
        EXPIRED
    }

    public boolean isActive(OffsetDateTime now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public Session revoke(RevokeReason reason, OffsetDateTime revokedAt) {
        return toBuilder().revokedAt(revokedAt).revokedReason(reason.name()).build();
    }

    @PrePersist
    void applyDefaults() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
