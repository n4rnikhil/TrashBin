package app.trashbin.auth.service;

import app.trashbin.auth.dto.AuthResponse;
import app.trashbin.auth.service.AccessTokenService.AccessToken;
import app.trashbin.auth.service.RefreshTokenService.IssuedToken;
import app.trashbin.common.error.InvalidRefreshTokenException;
import app.trashbin.common.error.RegistrationDisabledException;
import app.trashbin.common.security.AuthenticatedUser;
import app.trashbin.common.security.ClientInfo;
import app.trashbin.config.AuthProperties;
import app.trashbin.user.dto.UserProfile;
import app.trashbin.user.service.UserService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orchestrates sign-up, sign-in, session renewal and sign-out across users and tokens. */
@Service
public class AuthService {

    /** What the web layer needs: the response body plus the refresh token for the cookie. */
    public record AuthResult(
            AuthResponse response, String refreshToken, Instant refreshExpiresAt) {}

    private final UserService users;
    private final AccessTokenService accessTokens;
    private final RefreshTokenService refreshTokens;
    private final AuthProperties properties;

    public AuthService(
            UserService users,
            AccessTokenService accessTokens,
            RefreshTokenService refreshTokens,
            AuthProperties properties) {
        this.users = users;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.properties = properties;
    }

    public UserProfile register(String email, String displayName, String password) {
        if (!properties.registrationEnabled()) {
            throw new RegistrationDisabledException();
        }
        return users.register(email, displayName, password);
    }

    public AuthResult login(String email, String password, ClientInfo client) {
        UserProfile user = users.authenticate(email, password);
        return result(user, refreshTokens.startSession(user.id(), client));
    }

    public AuthResult refresh(String rawRefreshToken, ClientInfo client) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        IssuedToken rotated = refreshTokens.rotate(rawRefreshToken, client);
        UserProfile user =
                users.findActiveProfile(rotated.userId())
                        .orElseThrow(InvalidRefreshTokenException::new);
        return result(user, rotated);
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokens.endSession(rawRefreshToken);
        }
    }

    public void logoutEverywhere(AuthenticatedUser caller) {
        refreshTokens.revokeAll(caller.id());
    }

    /** A password change signs the account out everywhere, including the current session. */
    @Transactional
    public void changePassword(AuthenticatedUser caller, String current, String replacement) {
        users.changePassword(caller, current, replacement);
        refreshTokens.revokeAll(caller.id());
    }

    /**
     * Forgotten-password recovery run by the operator (ADR-003): new password, lockout cleared, and
     * every session ended so whoever might hold the old credentials is signed out.
     */
    @Transactional
    public UUID recoverAccount(String email, String replacement) {
        UUID userId = users.resetPassword(email, replacement);
        refreshTokens.revokeAll(userId);
        return userId;
    }

    private AuthResult result(UserProfile user, IssuedToken refresh) {
        AccessToken access = accessTokens.issue(user);
        return new AuthResult(
                new AuthResponse(access.value(), access.expiresInSeconds(), user),
                refresh.rawToken(),
                refresh.expiresAt());
    }
}
