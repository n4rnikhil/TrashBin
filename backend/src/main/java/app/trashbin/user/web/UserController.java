package app.trashbin.user.web;

import app.trashbin.common.security.AuthenticatedUser;
import app.trashbin.user.dto.UpdateProfileRequest;
import app.trashbin.user.dto.UserProfile;
import app.trashbin.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserProfile me(@AuthenticationPrincipal Jwt jwt) {
        return userService.getProfile(AuthenticatedUser.from(jwt));
    }

    @PatchMapping
    public UserProfile update(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(AuthenticatedUser.from(jwt), request.displayName());
    }
}
