package app.trashbin.archive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.trashbin.archive.domain.Previews;
import app.trashbin.common.security.Secrets;
import app.trashbin.config.AuthProperties;
import app.trashbin.config.ContentConfig;
import app.trashbin.config.ContentProperties;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ContentServiceTest {

    @Test
    void onlyAllowlistedTypesArePreviewable() {
        assertThat(Previews.kindOf("image/png")).isEqualTo(Previews.Kind.IMAGE);
        assertThat(Previews.kindOf("text/plain")).isEqualTo(Previews.Kind.TEXT);
        assertThat(Previews.kindOf("application/pdf")).isEqualTo(Previews.Kind.PDF);
        assertThat(Previews.isPreviewable("image/svg+xml")).isFalse();
        assertThat(Previews.isPreviewable("text/html")).isFalse();
        assertThat(Previews.isPreviewable("application/zip")).isFalse();
    }

    @Test
    void downloadNamesKeepTheExtensionAndCannotBreakTheHeader() {
        assertThat(ContentService.safeFilename("Lease", "scan_01.pdf")).isEqualTo("Lease.pdf");
        assertThat(ContentService.safeFilename("Lease.PDF", "scan_01.pdf")).isEqualTo("Lease.PDF");
        assertThat(ContentService.safeFilename("a\"b\r\nc/d\\e", "x.txt"))
                .isEqualTo("a_b__c_d_e.txt");
        assertThat(ContentService.safeFilename("   ", "noext")).isEqualTo("download");
    }

    @Test
    void startupRefusesWeakOrReusedSigningKeys() {
        String strong =
                Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
        String other =
                Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());
        ContentConfig config = new ContentConfig();

        assertThatThrownBy(() -> Secrets.decodeKey("c2hvcnQ=", "X"))
                .hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> Secrets.decodeKey("not base64!", "X"))
                .hasMessageContaining("Base64");
        assertThatThrownBy(() -> config.ticketSigningKey(content(strong), auth(strong)))
                .hasMessageContaining("must differ");
        assertThat(config.ticketSigningKey(content(strong), auth(other)).key()).isNotNull();
    }

    private static ContentProperties content(String secret) {
        return new ContentProperties(secret, Duration.ofSeconds(60), Duration.ofMinutes(5));
    }

    private static AuthProperties auth(String secret) {
        Duration d = Duration.ofMinutes(1);
        return new AuthProperties(
                secret, d, d, d, d, false, true, "http://localhost:3000", 10, 4, 5, d, null);
    }
}
