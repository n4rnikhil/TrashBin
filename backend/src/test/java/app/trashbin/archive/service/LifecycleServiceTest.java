package app.trashbin.archive.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LifecycleServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");

    @Test
    void retentionSetsThePurgeDate() {
        assertThat(LifecycleService.purgeAfter(NOW, 30))
                .isEqualTo(Instant.parse("2026-12-16T10:00:00Z"));
    }

    @Test
    void zeroRetentionDisablesAutomaticPurge() {
        assertThat(LifecycleService.purgeAfter(NOW, 0)).isNull();
    }
}
