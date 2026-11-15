package app.trashbin.auth.service;

import app.trashbin.auth.domain.RefreshToken;
import app.trashbin.auth.dto.SessionInfo;
import app.trashbin.auth.repository.RefreshTokenRepository;
import app.trashbin.common.error.InvalidRefreshTokenException;
import app.trashbin.common.error.ResourceNotFoundException;
import app.trashbin.common.security.AuthenticatedUser;
import app.trashbin.common.security.ClientInfo;
import app.trashbin.config.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh-token sessions: issue, rotate on every use, detect reuse, revoke
 * (docs/SECURITY_GUIDELINES.md 3.3). Tokens are opaque random values; only their hash is stored.
 */
@Service
public class RefreshTokenService {

    /** A freshly issued token: the raw value goes to the client once and is never stored. */
    public record IssuedToken(String rawToken, UUID userId, Instant expiresAt, UUID tokenId) {}

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository tokens;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository tokens, AuthProperties properties, Clock clock) {
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
    }

    /** Starts a new session (token family) after a successful sign-in. */
    @Transactional
    public IssuedToken startSession(UUID userId, ClientInfo client) {
        Instant now = Instant.now(clock);
        return issue(
                userId, UUID.randomUUID(), now.plus(properties.refreshTokenMaxAge()), now, client);
    }

    /**
     * Exchanges a token for its successor. Presenting an already-used token after the grace window
     * means it leaked, so the whole session is revoked; that revocation must survive the exception,
     * hence {@code noRollbackFor}.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public IssuedToken rotate(String rawToken, ClientInfo client) {
        Instant now = Instant.now(clock);
        RefreshToken current =
                tokens.findByTokenHashForUpdate(hash(rawToken))
                        .orElseThrow(InvalidRefreshTokenException::new);
        if (current.isRevoked() || current.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }
        if (current.isUsed()) {
            boolean withinGrace =
                    Duration.between(current.getUsedAt(), now)
                                    .compareTo(properties.refreshReuseGrace())
                            <= 0;
            if (!withinGrace) {
                int revoked = tokens.revokeFamily(current.getFamilyId(), now);
                log.warn(
                        "Refresh token reuse detected for user {}; revoked {} token(s) of session"
                                + " {}",
                        current.getUserId(),
                        revoked,
                        current.getFamilyId());
            }
            throw new InvalidRefreshTokenException();
        }
        IssuedToken successor =
                issue(
                        current.getUserId(),
                        current.getFamilyId(),
                        current.getFamilyExpiresAt(),
                        now,
                        client);
        current.markUsed(now, successor.tokenId());
        return successor;
    }

    /** Ends the session the token belongs to. Unknown tokens are ignored (logout is idempotent). */
    @Transactional
    public void endSession(String rawToken) {
        tokens.findByTokenHash(hash(rawToken))
                .ifPresent(t -> tokens.revokeFamily(t.getFamilyId(), Instant.now(clock)));
    }

    @Transactional
    public void revokeAll(UUID userId) {
        int revoked = tokens.revokeAllOfUser(userId, Instant.now(clock));
        log.info("Revoked {} refresh token(s) for user {}", revoked, userId);
    }

    /** Revokes one of the caller's own sessions; someone else's session is "not found". */
    @Transactional
    public void revokeSession(AuthenticatedUser caller, UUID familyId) {
        if (tokens.revokeFamilyOfUser(caller.id(), familyId, Instant.now(clock)) == 0) {
            throw new ResourceNotFoundException();
        }
    }

    @Transactional(readOnly = true)
    public List<SessionInfo> listSessions(AuthenticatedUser caller) {
        return tokens.findActiveByUser(caller.id(), Instant.now(clock)).stream()
                .map(
                        t ->
                                new SessionInfo(
                                        t.getFamilyId(),
                                        t.getIssuedAt(),
                                        t.getFamilyExpiresAt(),
                                        t.getUserAgent()))
                .toList();
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void deleteExpired() {
        int deleted = tokens.deleteExpiredBefore(Instant.now(clock).minus(Duration.ofDays(1)));
        if (deleted > 0) {
            log.info("Deleted {} expired refresh token(s)", deleted);
        }
    }

    private IssuedToken issue(
            UUID userId, UUID familyId, Instant familyExpiresAt, Instant now, ClientInfo client) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = now.plus(properties.refreshTokenTtl());
        if (expiresAt.isAfter(familyExpiresAt)) {
            expiresAt = familyExpiresAt;
        }
        RefreshToken saved =
                tokens.saveAndFlush(
                        new RefreshToken(
                                userId,
                                familyId,
                                hash(raw),
                                now,
                                expiresAt,
                                familyExpiresAt,
                                client.userAgent(),
                                client.ipAddress()));
        return new IssuedToken(raw, userId, expiresAt, saved.getId());
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
