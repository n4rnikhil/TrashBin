package app.trashbin.archive.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.trashbin.archive.domain.TypeGroup;
import app.trashbin.archive.service.UploadExceptions.FileSignatureMismatchException;
import app.trashbin.archive.service.UploadExceptions.UnsupportedFileTypeException;
import app.trashbin.config.UploadProperties;
import app.trashbin.content.MediaTypeDetector;
import app.trashbin.content.MediaTypeDetector.Detection;
import app.trashbin.support.FileFixtures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.util.unit.DataSize;

class UploadPolicyTest {

    @TempDir Path dir;

    private final MediaTypeDetector detector = new MediaTypeDetector();
    private final UploadPolicy policy =
            new UploadPolicy(
                    new UploadProperties(
                            DataSize.ofMegabytes(1),
                            DataSize.ofMegabytes(3),
                            List.of("exe", ".BAT")),
                    detector);

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "../../etc/passwd|passwd",
                "C:\\Users\\me\\report.pdf|report.pdf",
                "  notes.txt  |notes.txt",
                "..|untitled",
                "''|untitled",
            })
    void fileNamesAreReducedToSafeBaseNames(String raw, String expected) {
        assertThat(UploadPolicy.sanitizeFileName(raw)).isEqualTo(expected);
    }

    @Test
    void controlCharactersAndExcessLengthAreRemoved() {
        assertThat(UploadPolicy.sanitizeFileName("a\u0000b\nc.txt")).isEqualTo("abc.txt");
        assertThat(UploadPolicy.sanitizeFileName("x".repeat(300))).hasSize(255);
        assertThat(UploadPolicy.sanitizeFileName(null)).isEqualTo("untitled");
    }

    @Test
    void extensionsAreLowerCasedAndDotfilesHaveNone() {
        assertThat(UploadPolicy.extensionOf("Photo.JPG")).isEqualTo("jpg");
        assertThat(UploadPolicy.extensionOf(".gitignore")).isEmpty();
        assertThat(UploadPolicy.extensionOf("archive.")).isEmpty();
    }

    @Test
    void denylistIsCaseInsensitiveAndToleratesLeadingDots() {
        assertThatThrownBy(() -> policy.checkExtensionAllowed("setup.EXE"))
                .isInstanceOf(UnsupportedFileTypeException.class);
        assertThatThrownBy(() -> policy.checkExtensionAllowed("run.bat"))
                .isInstanceOf(UnsupportedFileTypeException.class);
        assertThatCode(() -> policy.checkExtensionAllowed("notes.txt")).doesNotThrowAnyException();
    }

    @Test
    void signaturesAreVerifiedForFormatsThatHaveThem() throws Exception {
        assertAccepted("image.png", FileFixtures.png("a"));
        assertAccepted("doc.pdf", FileFixtures.pdf("a"));
        assertAccepted("sheet.xlsx", FileFixtures.zip("a"));
        assertRejected("fake.png", FileFixtures.text("plain text"));
        assertRejected("fake.pdf", FileFixtures.png("a"));
        assertRejected("fake.jpg", FileFixtures.pdf("a"));
    }

    @Test
    void formatsWithoutReliableSignaturesAreAccepted() throws Exception {
        assertAccepted("notes.txt", FileFixtures.text("hello"));
        assertAccepted("data.unknownext", FileFixtures.filled(64, (byte) 7));
    }

    @Test
    void detectedTypesMapToTypeGroups() throws Exception {
        assertThat(group("photo.png", FileFixtures.png("g"))).isEqualTo(TypeGroup.IMAGE);
        assertThat(group("report.pdf", FileFixtures.pdf("g"))).isEqualTo(TypeGroup.DOCUMENT);
        assertThat(group("notes.txt", FileFixtures.text("g"))).isEqualTo(TypeGroup.TEXT);
        assertThat(group("bundle.zip", FileFixtures.zip("g"))).isEqualTo(TypeGroup.ARCHIVE);
        assertThat(TypeGroup.of("text/csv")).isEqualTo(TypeGroup.SPREADSHEET);
        assertThat(TypeGroup.of("application/octet-stream")).isEqualTo(TypeGroup.OTHER);
    }

    private TypeGroup group(String name, byte[] content) throws Exception {
        return TypeGroup.of(detect(name, content).effective());
    }

    private void assertAccepted(String name, byte[] content) throws Exception {
        Detection detection = detect(name, content);
        assertThatCode(() -> policy.checkSignature(name, detection))
                .as(name + " detected as " + detection)
                .doesNotThrowAnyException();
    }

    private void assertRejected(String name, byte[] content) throws Exception {
        Detection detection = detect(name, content);
        assertThatThrownBy(() -> policy.checkSignature(name, detection))
                .as(name + " detected as " + detection)
                .isInstanceOf(FileSignatureMismatchException.class);
    }

    private Detection detect(String name, byte[] content) throws Exception {
        Path file = Files.write(dir.resolve("f-" + System.nanoTime()), content);
        return detector.detect(file, name);
    }
}
