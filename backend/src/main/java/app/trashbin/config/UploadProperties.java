package app.trashbin.config;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Upload limits and policy (docs/PROJECT_REQUIREMENTS.md section 5).
 *
 * @param blockedExtensions extensions (without dot, case-insensitive) that are refused outright
 */
@Validated
@ConfigurationProperties(prefix = "trashbin.upload")
public record UploadProperties(
        @NotNull DataSize maxFileSize,
        @NotNull DataSize userQuota,
        List<String> blockedExtensions) {

    public UploadProperties {
        blockedExtensions =
                blockedExtensions == null
                        ? List.of()
                        : blockedExtensions.stream()
                                .map(
                                        e ->
                                                e.trim()
                                                        .toLowerCase(Locale.ROOT)
                                                        .replaceFirst("^\\.", ""))
                                .filter(e -> !e.isEmpty())
                                .toList();
    }
}
