package app.trashbin.common.error;

import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes of the public API (docs/API_CONTRACT.md section 4). Codes
 * are part of the contract: add new ones, never rename existing ones.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** Problem type slug, e.g. {@code validation-failed}. */
    public String slug() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
