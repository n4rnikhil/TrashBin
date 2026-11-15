package app.trashbin.auth.web;

import app.trashbin.auth.dto.ChangePasswordRequest;
import app.trashbin.auth.dto.SessionInfo;
import app.trashbin.auth.service.AuthService;
import app.trashbin.auth.service.RefreshTokenService;
import app.trashbin.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Password and session management for the signed-in account. */
@RestController
@RequestMapping("/api/v1/users/me")
public class AccountSecurityController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokens;
    private final RefreshCookie refreshCookie;

    public AccountSecurityController(
            AuthService authService,
            RefreshTokenService refreshTokens,
            RefreshCookie refreshCookie) {
        this.authService = authService;
        this.refreshTokens = refreshTokens;
        this.refreshCookie = refreshCookie;
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(
                AuthenticatedUser.from(jwt), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear())
                .build();
    }

    @GetMapping("/sessions")
    public List<SessionInfo> sessions(@AuthenticationPrincipal Jwt jwt) {
        return refreshTokens.listSessions(AuthenticatedUser.from(jwt));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        refreshTokens.revokeSession(AuthenticatedUser.from(jwt), sessionId);
        return ResponseEntity.noContent().build();
    }
}
