package app.trashbin.storage;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Blob storage settings.
 *
 * @param root absolute path of the storage root. Must be outside any git working tree; supplied via
 *     {@code TRASHBIN_STORAGE_ROOT}.
 */
@Validated
@ConfigurationProperties(prefix = "trashbin.storage")
public record StorageProperties(@NotBlank String root) {}
