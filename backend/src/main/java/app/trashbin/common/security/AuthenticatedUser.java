package app.trashbin.common.security;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The caller's identity, taken only from the verified access token. Services receive this instead
 * of raw ids so an owner can never be supplied by request data.
 */
public record AuthenticatedUser(UUID id) {

    public static AuthenticatedUser from(Jwt jwt) {
        return new AuthenticatedUser(UUID.fromString(jwt.getSubject()));
    }
}
