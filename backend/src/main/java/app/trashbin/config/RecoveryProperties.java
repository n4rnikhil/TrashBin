package app.trashbin.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Input for the one-shot password recovery command ({@code recovery} profile, ADR-003). Bound only
 * in {@code application-recovery.yml}; the password comes from an environment variable so it never
 * appears in a command line or shell history.
 *
 * @param email the account to recover
 * @param password the new password
 */
@ConfigurationProperties(prefix = "trashbin.recovery")
public record RecoveryProperties(@Nullable String email, @Nullable String password) {}
