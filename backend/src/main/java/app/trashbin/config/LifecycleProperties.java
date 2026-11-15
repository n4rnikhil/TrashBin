package app.trashbin.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Discard/purge rules (FR-L4).
 *
 * @param retentionDays how long a discarded file stays restorable; {@code 0} disables automatic
 *     purging (files stay in Discarded until purged by hand)
 */
@Validated
@ConfigurationProperties(prefix = "trashbin.lifecycle")
public record LifecycleProperties(@Min(0) @Max(3650) int retentionDays) {}
