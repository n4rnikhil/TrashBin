package app.trashbin.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Text extraction limits (docs/SECURITY_GUIDELINES.md section 2, parser exploitation).
 *
 * @param background run automatically after uploads and on a schedule; tests turn this off and
 *     trigger extraction explicitly
 * @param maxInput larger files are not parsed (status SKIPPED)
 * @param maxChars extracted text beyond this is cut off (stored as truncated)
 * @param batchSize files handled per scheduled sweep
 */
@Validated
@ConfigurationProperties(prefix = "trashbin.extraction")
public record ExtractionProperties(
        boolean background,
        @NotNull DataSize maxInput,
        @Min(1000) int maxChars,
        @NotNull Duration timeout,
        @Min(1) int batchSize) {}
