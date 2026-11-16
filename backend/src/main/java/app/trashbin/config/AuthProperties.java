package app.trashbin.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Authentication settings (docs/SYSTEM_ARCHITECTURE.md configuration reference).
 *
 * @param jwtSecret Base64 key for signing access tokens; at least 32 decoded bytes
 * @param refreshReuseGrace window after rotation in which a replayed token is rejected without
 *     revoking the session (tolerates multi-tab races)
 * @param bootstrapAdminEmail the account registering with this email becomes ADMIN; optional
 */
@Validated
@ConfigurationProperties(prefix = "trashbin.auth")
public record AuthProperties(
        @NotBlank String jwtSecret,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @NotNull Duration refreshTokenMaxAge,
        @NotNull Duration refreshReuseGrace,
        boolean cookieSecure,
        boolean registrationEnabled,
        @NotBlank String allowedOrigin,
        @Min(1) int rateLimitPerMinute,
        @Min(4) @Max(16) int bcryptCost,
        @Min(1) int maxFailedLogins,
        @NotNull Duration lockDuration,
        @Nullable String bootstrapAdminEmail) {}
