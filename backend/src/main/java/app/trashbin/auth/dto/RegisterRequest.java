package app.trashbin.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The 72-byte password ceiling is enforced in the service (it is a byte, not character, limit). */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank @Size(min = 12, max = 72) String password) {}
