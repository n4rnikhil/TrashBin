package app.trashbin.auth.web;

import app.trashbin.auth.dto.AuthResponse;
import app.trashbin.auth.dto.LoginRequest;
import app.trashbin.auth.dto.RegisterRequest;
import app.trashbin.auth.service.AuthService;
import app.trashbin.auth.service.AuthService.AuthResult;
import app.trashbin.common.security.AuthenticatedUser;
import app.trashbin.common.security.ClientInfo;
import app.trashbin.user.dto.UserProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookie refreshCookie;

    public AuthController(AuthService authService, RefreshCookie refreshCookie) {
        this.authService = authService;
        this.refreshCookie = refreshCookie;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserProfile register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request.email(), request.displayName(), request.password());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return withCookie(
                authService.login(request.email(), request.password(), ClientInfo.from(http)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = RefreshCookie.NAME, required = false) String token,
            HttpServletRequest http) {
        refreshCookie.requireSameOrigin(http);
        return withCookie(authService.refresh(token, ClientInfo.from(http)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookie.NAME, required = false) String token,
            HttpServletRequest http) {
        refreshCookie.requireSameOrigin(http);
        authService.logout(token);
        return cleared();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal Jwt jwt) {
        authService.logoutEverywhere(AuthenticatedUser.from(jwt));
        return cleared();
    }

    private ResponseEntity<AuthResponse> withCookie(AuthResult result) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshCookie.issue(result.refreshToken(), result.refreshExpiresAt()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.response());
    }

    private ResponseEntity<Void> cleared() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear())
                .build();
    }
}
