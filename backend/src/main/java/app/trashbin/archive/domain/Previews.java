package app.trashbin.archive.domain;

import java.util.Set;

/**
 * Which stored types may be shown inline, and how (docs/SECURITY_GUIDELINES.md section 7). Anything
 * not listed — notably SVG and HTML, which can carry script — is only ever downloaded.
 */
public final class Previews {

    public enum Kind {
        IMAGE,
        TEXT,
        PDF
    }

    private static final Set<String> IMAGES =
            Set.of("image/png", "image/jpeg", "image/gif", "image/webp");
    private static final Set<String> TEXTS =
            Set.of("text/plain", "text/csv", "text/markdown", "text/x-web-markdown");
    private static final String PDF = "application/pdf";

    private Previews() {}

    /** Null when the type must not be previewed. */
    public static Kind kindOf(String mediaType) {
        if (IMAGES.contains(mediaType)) {
            return Kind.IMAGE;
        }
        if (TEXTS.contains(mediaType)) {
            return Kind.TEXT;
        }
        return PDF.equals(mediaType) ? Kind.PDF : null;
    }

    public static boolean isPreviewable(String mediaType) {
        return kindOf(mediaType) != null;
    }
}
