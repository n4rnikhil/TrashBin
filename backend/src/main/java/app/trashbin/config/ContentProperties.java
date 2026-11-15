package app.trashbin.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Download and preview links (docs/SECURITY_GUIDELINES.md section 7).
 *
 * @param ticketSecret Base64 key for signing access tickets; must differ from the JWT key
 * @param previewTicketTtl longer than downloads because viewers keep issuing range requests
 */
@Validated
@ConfigurationProperties(prefix = "trashbin.content")
public record ContentProperties(
        @NotBlank String ticketSecret,
        @NotNull Duration downloadTicketTtl,
        @NotNull Duration previewTicketTtl) {}
