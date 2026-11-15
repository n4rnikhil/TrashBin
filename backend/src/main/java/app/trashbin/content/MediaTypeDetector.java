package app.trashbin.content;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.springframework.stereotype.Component;

/**
 * Determines file types from content with Apache Tika. Only the first bytes are read; nothing is
 * parsed or executed.
 */
@Component
public class MediaTypeDetector {

    /**
     * @param fromContent type judged from the bytes alone (file signature)
     * @param effective type judged from bytes plus the file name; what gets stored
     */
    public record Detection(String fromContent, String effective) {}

    private final Detector detector;
    private final MediaTypeRegistry registry;

    public MediaTypeDetector() {
        TikaConfig config = TikaConfig.getDefaultConfig();
        this.detector = config.getDetector();
        this.registry = config.getMediaTypeRegistry();
    }

    public Detection detect(Path file, String fileName) {
        Metadata withName = new Metadata();
        withName.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        return new Detection(detect(file, new Metadata()), detect(file, withName));
    }

    /** True when {@code actual} is {@code expected} or a more specific subtype of it. */
    public boolean isA(String actual, String expected) {
        MediaType a = MediaType.parse(actual);
        MediaType e = MediaType.parse(expected);
        return a != null && e != null && (a.equals(e) || registry.isSpecializationOf(a, e));
    }

    private String detect(Path file, Metadata metadata) {
        try (TikaInputStream stream = TikaInputStream.get(file)) {
            return detector.detect(stream, metadata).getBaseType().toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not inspect file type", e);
        }
    }
}
