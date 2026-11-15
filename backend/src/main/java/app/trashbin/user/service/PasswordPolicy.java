package app.trashbin.user.service;

import app.trashbin.common.error.InvalidRequestException;
import java.nio.charset.StandardCharsets;

/**
 * Password rules (docs/SECURITY_GUIDELINES.md 3.1): at least 12 characters and at most 72 bytes in
 * UTF-8. BCrypt ignores everything after 72 bytes, so longer input is rejected instead of being
 * silently truncated.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {}

    public static void validate(String password) {
        if (password.length() < MIN_LENGTH) {
            throw new InvalidRequestException(
                    "Password must be at least " + MIN_LENGTH + " characters.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new InvalidRequestException(
                    "Password must be at most " + MAX_BYTES + " bytes when encoded as UTF-8.");
        }
    }
}
