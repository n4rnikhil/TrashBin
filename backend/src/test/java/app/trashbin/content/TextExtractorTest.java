package app.trashbin.content;

import static org.assertj.core.api.Assertions.assertThat;

import app.trashbin.archive.domain.Extractable;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TextExtractorTest {

    private final TextExtractor extractor = new TextExtractor();

    @Test
    void extractsPlainTextAndReportsTruncation() throws Exception {
        TextExtractor.Extracted full = extract("short text", 1000);
        TextExtractor.Extracted cut = extract("x".repeat(5000), 1000);

        assertThat(full.text()).isEqualTo("short text");
        assertThat(full.truncated()).isFalse();
        assertThat(cut.truncated()).isTrue();
        assertThat(cut.text().length()).isLessThanOrEqualTo(1000);
    }

    @Test
    void cleansCharactersPostgresCannotStoreAndExcessBlankLines() {
        assertThat(TextExtractor.clean("a\u0000b\n\n\n\n\nc  \n")).isEqualTo("ab\n\nc");
    }

    @Test
    void onlyDocumentAndTextTypesAreExtracted() {
        assertThat(Extractable.isExtractable("application/pdf")).isTrue();
        assertThat(Extractable.isExtractable("text/csv")).isTrue();
        assertThat(Extractable.isExtractable("image/png")).isFalse();
        assertThat(Extractable.isExtractable("application/zip")).isFalse();
        assertThat(Extractable.isExtractable("text/html")).isFalse();
    }

    private TextExtractor.Extracted extract(String text, int maxChars) throws Exception {
        return extractor.extract(
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),
                "text/plain",
                maxChars);
    }
}
