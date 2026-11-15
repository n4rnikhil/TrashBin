package app.trashbin.tag.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * A validated tag name. Display form: trimmed, inner whitespace collapsed, control characters
 * removed. Normalized form (for uniqueness): display form lower-cased.
 */
public record TagName(String display, String normalized) {

    public static final int MAX_LENGTH = 50;

    /** Empty when the input is blank or longer than {@link #MAX_LENGTH} after cleaning. */
    public static Optional<TagName> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String display = raw.replaceAll("\\p{Cntrl}", " ").strip().replaceAll("\\s+", " ");
        if (display.isEmpty() || display.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(new TagName(display, display.toLowerCase(Locale.ROOT)));
    }
}
