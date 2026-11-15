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
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Session expired"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),
    REGISTRATION_DISABLED(HttpStatus.FORBIDDEN, "Registration disabled"),
    TICKET_INVALID(HttpStatus.FORBIDDEN, "Link invalid or expired"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "Email already registered"),
    DUPLICATE_FILE(HttpStatus.CONFLICT, "Duplicate file"),
    TAG_ALREADY_EXISTS(HttpStatus.CONFLICT, "Tag already exists"),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "Not possible in the current state"),
    COLLECTION_ALREADY_EXISTS(HttpStatus.CONFLICT, "Collection already exists"),
    VERSION_CONFLICT(HttpStatus.PRECONDITION_FAILED, "Version conflict"),
    PRECONDITION_REQUIRED(HttpStatus.PRECONDITION_REQUIRED, "Precondition required"),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "File too large"),
    QUOTA_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, "Storage quota exceeded"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported file type"),
    FILE_SIGNATURE_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "File content does not match"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
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
