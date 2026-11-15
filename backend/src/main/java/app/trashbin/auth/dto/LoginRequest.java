package app.trashbin.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Size caps only bound the work done per attempt; they are not the password policy. */
public record LoginRequest(
        @NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 200) String password) {}
