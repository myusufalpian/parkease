package id.xyz.parkease.repository;

import id.xyz.parkease.domain.Session;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionRepository extends JpaRepository<Session, UUID> {

    Optional<Session> findByRefreshTokenHash(String refreshTokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Session s SET s.revokedAt = :now, s.revokedReason = :reason "
            + "WHERE s.refreshTokenHash = :hash AND s.revokedAt IS NULL AND s.expiresAt > :now")
    int revokeActiveByHash(
            @Param("hash") String refreshTokenHash,
            @Param("reason") String reason,
            @Param("now") OffsetDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Session s SET s.revokedAt = :now, s.revokedReason = :reason "
            + "WHERE s.customerAccount.id = :accountId AND s.revokedAt IS NULL")
    int revokeAllActiveForAccount(
            @Param("accountId") UUID customerAccountId,
            @Param("reason") String reason,
            @Param("now") OffsetDateTime now);
}
