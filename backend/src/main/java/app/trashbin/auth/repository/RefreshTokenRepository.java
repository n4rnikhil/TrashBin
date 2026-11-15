package app.trashbin.auth.repository;

import app.trashbin.auth.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Row lock so two concurrent refreshes with the same token cannot both rotate it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") String hash);

    Optional<RefreshToken> findByTokenHash(String hash);

    /** The single live token of each of the user's sessions. */
    @Query(
            """
            select t from RefreshToken t
            where t.userId = :userId and t.revokedAt is null and t.usedAt is null
              and t.expiresAt > :now and t.familyExpiresAt > :now
            order by t.issuedAt desc
            """)
    List<RefreshToken> findActiveByUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query(
            "update RefreshToken t set t.revokedAt = :now"
                    + " where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Modifying
    @Query(
            "update RefreshToken t set t.revokedAt = :now"
                    + " where t.userId = :userId and t.familyId = :familyId and t.revokedAt is null")
    int revokeFamilyOfUser(
            @Param("userId") UUID userId,
            @Param("familyId") UUID familyId,
            @Param("now") Instant now);

    @Modifying
    @Query(
            "update RefreshToken t set t.revokedAt = :now"
                    + " where t.userId = :userId and t.revokedAt is null")
    int revokeAllOfUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from RefreshToken t where t.familyExpiresAt < :cutoff or t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
