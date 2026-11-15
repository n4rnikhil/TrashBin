package app.trashbin.auth.web;

import app.trashbin.common.error.ForbiddenException;
import app.trashbin.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The refresh-token cookie and the CSRF guard for the endpoints that read it
 * (docs/SECURITY_GUIDELINES.md 3.3, 3.6). The cookie is HttpOnly, SameSite=Strict and scoped to the
 * auth path so it is sent nowhere else.
 */
@Component
public class RefreshCookie {

    public static final String NAME = "tb_refresh";
    static final String PATH = "/api/v1/auth";
    static final String GUARD_HEADER = "X-Requested-With";
    static final String GUARD_VALUE = "trashbin";

    private final AuthProperties properties;
    private final Clock clock;

    public RefreshCookie(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String issue(String rawToken, Instant expiresAt) {
        return build(rawToken, Duration.between(Instant.now(clock), expiresAt));
    }

    public String clear() {
        return build("", Duration.ZERO);
    }

    /**
     * Cookie-authenticated requests must come from our own frontend: a custom header (which
     * cross-site forms cannot set) and, when the browser sends one, a matching Origin.
     */
    public void requireSameOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        boolean headerOk = GUARD_VALUE.equals(request.getHeader(GUARD_HEADER));
        boolean originOk = origin == null || origin.equals(properties.allowedOrigin());
        if (!headerOk || !originOk) {
            throw new ForbiddenException();
        }
    }

    private String build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge)
                .build()
                .toString();
    }
}
