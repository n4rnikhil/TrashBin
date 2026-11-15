package app.trashbin.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * One link in a refresh-token rotation chain. Only the SHA-256 hash of the token is stored. All
 * tokens issued from one sign-in share a {@code familyId}.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id @GeneratedValue @UuidGenerator private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "family_expires_at", nullable = false)
    private Instant familyExpiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    protected RefreshToken() {}

    public RefreshToken(
            UUID userId,
            UUID familyId,
            String tokenHash,
            Instant issuedAt,
            Instant expiresAt,
            Instant familyExpiresAt,
            String userAgent,
            String ipAddress) {
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.familyExpiresAt = familyExpiresAt;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now) || !familyExpiresAt.isAfter(now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void markUsed(Instant now, UUID successorId) {
        this.usedAt = now;
        this.replacedById = successorId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getFamilyExpiresAt() {
        return familyExpiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public String getUserAgent() {
        return userAgent;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RefreshToken token && id != null && id.equals(token.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
