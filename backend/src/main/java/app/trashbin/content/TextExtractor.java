package app.trashbin.content;

import java.io.IOException;
import java.io.InputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

/**
 * Plain-text extraction with Apache Tika. Only text is produced: embedded resources are not written
 * anywhere and nothing is executed. AutoDetectParser applies Tika's zip-bomb protection; the caller
 * enforces input size and time limits.
 */
@Component
public class TextExtractor {

    /** {@code truncated} is true when the document held more text than was kept. */
    public record Extracted(String text, boolean truncated) {}

    private final AutoDetectParser parser = new AutoDetectParser();

    public Extracted extract(InputStream content, String mediaType, int maxChars)
            throws IOException, TikaException, SAXException {
        BodyContentHandler handler = new BodyContentHandler(maxChars);
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, mediaType);
        boolean truncated = false;
        try {
            parser.parse(content, handler, metadata, new ParseContext());
        } catch (SAXException e) {
            if (!WriteLimitReachedException.isWriteLimitReached(e)) {
                throw e;
            }
            truncated = true; // keep what was collected up to the limit
        }
        return new Extracted(clean(handler.toString()), truncated);
    }

    /** PostgreSQL text cannot hold NUL; long runs of blank lines carry no meaning. */
    static String clean(String raw) {
        return raw.replace("\u0000", "")
                .replaceAll("[ \\t\\x0B\\f\\r]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }
}
