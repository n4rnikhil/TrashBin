package app.trashbin.user.domain;

public enum UserRole {
    USER,
    /** Operations only (actuator, maintenance). Grants no access to other users' files. */
    ADMIN
}
