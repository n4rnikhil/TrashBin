package app.trashbin.auth.service;

import app.trashbin.config.RecoveryProperties;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * One-shot operator command for a forgotten password (ADR-003). Active only with the {@code
 * recovery} profile, which also turns the web server off; it resets the account, ends all its
 * sessions and exits. Run it through {@code scripts/reset-password.ps1}.
 */
@Component
@Profile("recovery")
class PasswordRecoveryCommand implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryCommand.class);

    private final AuthService auth;
    private final RecoveryProperties properties;
    private final ConfigurableApplicationContext context;

    PasswordRecoveryCommand(
            AuthService auth,
            RecoveryProperties properties,
            ConfigurableApplicationContext context) {
        this.auth = auth;
        this.properties = properties;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = properties.email();
        String password = properties.password();
        if (email == null || email.isBlank() || password == null || password.isEmpty()) {
            throw new IllegalStateException(
                    "Set TRASHBIN_RECOVERY_EMAIL and TRASHBIN_RECOVERY_PASSWORD for the recovery"
                            + " profile");
        }
        UUID userId = auth.recoverAccount(email, password);
        log.warn("Recovery complete for user {}: new password set, all sessions revoked", userId);
        // Scheduled jobs keep non-daemon threads alive, so leave explicitly once the work is done.
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
