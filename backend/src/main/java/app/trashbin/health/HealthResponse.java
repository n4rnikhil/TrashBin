package app.trashbin.health;

import java.time.Instant;

/** Public liveness/readiness payload. Deliberately minimal: no versions, hosts or error details. */
public record HealthResponse(String status, String database, Instant timestamp) {

    public static final String UP = "UP";
    public static final String DOWN = "DOWN";
}
