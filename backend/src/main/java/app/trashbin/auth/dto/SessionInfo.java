package app.trashbin.auth.dto;

import java.time.Instant;
import java.util.UUID;

/** A signed-in device or browser. {@code id} identifies the session for revocation. */
public record SessionInfo(UUID id, Instant lastActiveAt, Instant expiresAt, String userAgent) {}
