package app.trashbin.archive.service;

import app.trashbin.config.ContentConfig.TicketSigningKey;
import app.trashbin.config.ContentProperties;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import org.springframework.stereotype.Service;

/**
 * Short-lived, signed tokens for downloading or previewing one file (docs/SECURITY_GUIDELINES.md
 * section 7). Browsers cannot attach the bearer token to {@code <a>}, {@code <img>} or {@code
 * <iframe>} requests, so these URLs carry their own proof instead.
 *
 * <p>Format: {@code base64url(payload) "." base64url(HMAC-SHA256(payload))}, payload {@code
 * v1|userId|fileId|purpose|expiresEpochSeconds}. A valid ticket is never sufficient on its own: the
 * content endpoint re-checks that the file exists and belongs to the ticket's user.
 */
@Service
public class AccessTicketService {

    public enum Purpose {
        DOWNLOAD,
        PREVIEW
    }

    public record Ticket(String token, Instant expiresAt) {}

    public record VerifiedTicket(UUID userId, UUID fileId, Purpose purpose) {}

    private static final String VERSION = "v1";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final TicketSigningKey key;
    private final ContentProperties properties;
    private final Clock clock;

    public AccessTicketService(TicketSigningKey key, ContentProperties properties, Clock clock) {
        this.key = key;
        this.properties = properties;
        this.clock = clock;
    }

    public Ticket issue(UUID userId, UUID fileId, Purpose purpose) {
        Duration ttl =
                purpose == Purpose.PREVIEW
                        ? properties.previewTicketTtl()
                        : properties.downloadTicketTtl();
        Instant expiresAt = Instant.now(clock).plus(ttl);
        String payload =
                String.join(
                        "|",
                        VERSION,
                        userId.toString(),
                        fileId.toString(),
                        purpose.name(),
                        Long.toString(expiresAt.getEpochSecond()));
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        String token =
                ENCODER.encodeToString(payloadBytes)
                        + "."
                        + ENCODER.encodeToString(sign(payloadBytes));
        return new Ticket(token, expiresAt);
    }

    /** Empty for anything malformed, forged or expired; callers must not reveal which. */
    public Optional<VerifiedTicket> verify(String token) {
        if (token == null || token.length() > 512) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot != token.lastIndexOf('.')) {
            return Optional.empty();
        }
        byte[] payloadBytes;
        byte[] signature;
        try {
            payloadBytes = DECODER.decode(token.substring(0, dot));
            signature = DECODER.decode(token.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        // Constant-time comparison: no timing hints about how much of a forged signature matched.
        if (!MessageDigest.isEqual(sign(payloadBytes), signature)) {
            return Optional.empty();
        }

        String[] parts = new String(payloadBytes, StandardCharsets.UTF_8).split("\\|", -1);
        if (parts.length != 5 || !VERSION.equals(parts[0])) {
            return Optional.empty();
        }
        try {
            Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(parts[4]));
            if (!Instant.now(clock).isBefore(expiresAt)) {
                return Optional.empty();
            }
            return Optional.of(
                    new VerifiedTicket(
                            UUID.fromString(parts[1]),
                            UUID.fromString(parts[2]),
                            Purpose.valueOf(parts[3])));
        } catch (IllegalArgumentException e) { // includes NumberFormatException
            return Optional.empty();
        }
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key.key());
            return mac.doFinal(payload);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 is required by the Java platform", e);
        }
    }
}
