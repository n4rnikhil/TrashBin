package app.trashbin.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.trashbin.config.AuthProperties;
import app.trashbin.support.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(properties(3), clock);

    @Test
    void allowsUpToTheLimitThenBlocksUntilTheNextWindow() {
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
        assertThat(limiter.retryAfterSeconds()).isBetween(1L, 60L);

        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("a")).isTrue();
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire("a");
        }
        assertThat(limiter.tryAcquire("b")).isTrue();
    }

    private static AuthProperties properties(int limit) {
        Duration d = Duration.ofMinutes(1);
        return new AuthProperties(
                "unused", d, d, d, d, false, true, "http://localhost:3000", limit, 4, 5, d, null);
    }
}
