package app.trashbin.user.dto;

import java.util.UUID;

/** Public view of an account. Never contains credentials or lockout state. */
public record UserProfile(UUID id, String email, String displayName, String role) {}
