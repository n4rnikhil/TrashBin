package app.trashbin.auth.dto;

import app.trashbin.user.dto.UserProfile;

/** The refresh token is never part of a body; it travels only as an HttpOnly cookie. */
public record AuthResponse(String accessToken, long expiresIn, UserProfile user) {}
