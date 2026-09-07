package id.xyz.parkease.domain;

import id.xyz.parkease.domain.Session.RevokeReason;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTest {

    private static final OffsetDateTime ISSUED_AT = OffsetDateTime.parse("2024-01-15T08:00:00Z");
    private static final OffsetDateTime EXPIRES_AT = OffsetDateTime.parse("2024-01-22T08:00:00Z");
    private static final String TOKEN_HASH = "token-hash-value";

    private CustomerAccount account() {
        return CustomerAccount.builder().username("driver1").passwordHash("hash").build();
    }

    private Session session() {
        return Session.builder()
                .customerAccount(account())
                .refreshTokenHash(TOKEN_HASH)
                .issuedAt(ISSUED_AT)
                .expiresAt(EXPIRES_AT)
                .build();
    }

    @Test
    void buildWithRequiredFields() {
        Session session = session();
        assertNotNull(session.getId());
        assertEquals(TOKEN_HASH, session.getRefreshTokenHash());
        assertEquals(ISSUED_AT, session.getIssuedAt());
        assertEquals(EXPIRES_AT, session.getExpiresAt());
        assertNull(session.getRevokedAt());
        assertNull(session.getRevokedReason());
    }

    @Test
    void isActiveBeforeExpiryAndNotRevoked() {
        assertTrue(session().isActive(EXPIRES_AT.minusSeconds(1)));
    }

    @Test
    void isActiveFalseAtExactExpiryBoundary() {
        assertFalse(session().isActive(EXPIRES_AT));
    }

    @Test
    void isActiveFalseAfterExpiry() {
        assertFalse(session().isActive(EXPIRES_AT.plusSeconds(1)));
    }

    @Test
    void isActiveFalseWhenRevoked() {
        Session revoked = session().revoke(RevokeReason.LOGOUT, ISSUED_AT.plusMinutes(5));
        assertFalse(revoked.isActive(ISSUED_AT.plusMinutes(6)));
    }

    @Test
    void revokeSetsRevokedAtAndReason() {
        OffsetDateTime revokedAt = ISSUED_AT.plusMinutes(10);
        Session revoked = session().revoke(RevokeReason.ROTATED, revokedAt);
        assertEquals(revokedAt, revoked.getRevokedAt());
        assertEquals(RevokeReason.ROTATED.name(), revoked.getRevokedReason());
    }

    @Test
    void applyDefaultsFillsNulls() {
        Session session = new Session(null, account(), TOKEN_HASH, ISSUED_AT, EXPIRES_AT, null, null, null, null, null, null, null, null);
        session.applyDefaults();
        assertNotNull(session.getId());
        assertNotNull(session.getCreatedAt());
    }

    @Test
    void applyDefaultsKeepsExistingId() {
        Session session = session();
        UUID id = session.getId();
        session.applyDefaults();
        assertEquals(id, session.getId());
    }
}
