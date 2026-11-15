package app.trashbin.auth.service;

import app.trashbin.config.AuthProperties;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Small in-memory per-key request limiter for the authentication endpoints. One-minute fixed
 * windows are coarse but sufficient to blunt brute force on a single-node deployment; account
 * lockout is the second line of defense.
 */
@Component
public class FixedWindowRateLimiter {

    private record Window(long minute, int count) {}

    private static final int MAX_TRACKED_KEYS = 10_000;
    private static final long WINDOW_MILLIS = 60_000;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;
    private final Clock clock;

    public FixedWindowRateLimiter(AuthProperties properties, Clock clock) {
        this.limit = properties.rateLimitPerMinute();
        this.clock = clock;
    }

    /** Returns false when {@code key} has exhausted its budget for the current minute. */
    public boolean tryAcquire(String key) {
        long minute = clock.millis() / WINDOW_MILLIS;
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.entrySet().removeIf(e -> e.getValue().minute() != minute);
        }
        Window updated =
                windows.compute(
                        key,
                        (k, w) ->
                                w == null || w.minute() != minute
                                        ? new Window(minute, 1)
                                        : new Window(minute, w.count() + 1));
        return updated.count() <= limit;
    }

    /** Seconds until the current window ends. */
    public long retryAfterSeconds() {
        return Math.max(1, (WINDOW_MILLIS - clock.millis() % WINDOW_MILLIS) / 1000);
    }
}
