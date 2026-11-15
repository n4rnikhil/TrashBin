package app.trashbin.archive.service;

import app.trashbin.archive.service.UploadExceptions.FileSignatureMismatchException;
import app.trashbin.archive.service.UploadExceptions.UnsupportedFileTypeException;
import app.trashbin.config.UploadProperties;
import app.trashbin.content.MediaTypeDetector;
import app.trashbin.content.MediaTypeDetector.Detection;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Upload rules (docs/SECURITY_GUIDELINES.md section 5): file-name hygiene, the extension denylist,
 * and signature verification for formats that have a reliable signature. Other formats are
 * accepted; they are stored and served as downloads, never executed.
 */
@Component
public class UploadPolicy {

    static final int MAX_NAME_LENGTH = 255;
    private static final String FALLBACK_NAME = "untitled";

    /** Extension -> the type its first bytes must show. OOXML formats are ZIP containers. */
    private static final Map<String, String> SIGNATURES =
            Map.ofEntries(
                    Map.entry("pdf", "application/pdf"),
                    Map.entry("png", "image/png"),
                    Map.entry("jpg", "image/jpeg"),
                    Map.entry("jpeg", "image/jpeg"),
                    Map.entry("gif", "image/gif"),
                    Map.entry("webp", "image/webp"),
                    Map.entry("zip", "application/zip"),
                    Map.entry("docx", "application/zip"),
                    Map.entry("xlsx", "application/zip"),
                    Map.entry("pptx", "application/zip"));

    private final UploadProperties properties;
    private final MediaTypeDetector detector;

    public UploadPolicy(UploadProperties properties, MediaTypeDetector detector) {
        this.properties = properties;
        this.detector = detector;
    }

    /**
     * The original name reduced to a safe display string: no directories, no control characters,
     * bounded length. It is metadata only and never used to build a path.
     */
    public static String sanitizeFileName(String raw) {
        if (raw == null) {
            return FALLBACK_NAME;
        }
        String name = raw.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return FALLBACK_NAME;
        }
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    public static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 || dot == fileName.length() - 1
                ? ""
                : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public void checkExtensionAllowed(String fileName) {
        if (properties.blockedExtensions().contains(extensionOf(fileName))) {
            throw new UnsupportedFileTypeException();
        }
    }

    public void checkSignature(String fileName, Detection detection) {
        String expected = SIGNATURES.get(extensionOf(fileName));
        if (expected != null && !detector.isA(detection.fromContent(), expected)) {
            throw new FileSignatureMismatchException();
        }
    }
}
