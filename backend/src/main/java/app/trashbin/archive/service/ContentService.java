package app.trashbin.archive.service;

import app.trashbin.activity.domain.FileEventType;
import app.trashbin.activity.service.ActivityService;
import app.trashbin.archive.domain.ArchivedFile;
import app.trashbin.archive.domain.Previews;
import app.trashbin.archive.repository.ArchivedFileRepository;
import app.trashbin.archive.service.AccessTicketService.Purpose;
import app.trashbin.archive.service.AccessTicketService.Ticket;
import app.trashbin.archive.service.AccessTicketService.VerifiedTicket;
import app.trashbin.common.error.ErrorCode;
import app.trashbin.common.error.ResourceNotFoundException;
import app.trashbin.common.error.TrashBinException;
import app.trashbin.common.security.AuthenticatedUser;
import app.trashbin.storage.BlobStore;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues download/preview links and resolves them to the stored bytes plus safe headers. */
@Service
public class ContentService {

    /** Previews are offered only for an allowlist of types; everything else is download-only. */
    public static class PreviewNotAvailableException extends TrashBinException {
        private static final long serialVersionUID = 1L;

        public PreviewNotAvailableException() {
            super(
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "This file cannot be previewed. Download it instead.");
        }
    }

    /** Everything the web layer needs to stream one file safely. */
    public record ServedContent(
            Resource resource,
            MediaType contentType,
            ContentDisposition disposition,
            String etag,
            String contentSecurityPolicy) {}

    /**
     * Locks the response down: no scripts, no plugins, framing only by our own pages. PDFs omit
     * {@code sandbox}, which disables the browsers' built-in PDF viewers (ADR-004).
     */
    static final String CSP_STRICT =
            "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; sandbox;"
                    + " frame-ancestors 'self'";

    static final String CSP_PDF = "frame-ancestors 'self'";

    private static final Logger log = LoggerFactory.getLogger(ContentService.class);
    private static final int MAX_FILENAME_LENGTH = 200;

    private final ArchivedFileRepository files;
    private final BlobStore blobs;
    private final AccessTicketService tickets;
    private final ActivityService activity;
    private final Clock clock;

    public ContentService(
            ArchivedFileRepository files,
            BlobStore blobs,
            AccessTicketService tickets,
            ActivityService activity,
            Clock clock) {
        this.files = files;
        this.blobs = blobs;
        this.tickets = tickets;
        this.activity = activity;
        this.clock = clock;
    }

    /**
     * Creates a link for the caller's own file. Each request counts as one access: it is recorded
     * and updates "last accessed" without changing the file's version (no edit conflicts).
     */
    @Transactional
    public Ticket createTicket(AuthenticatedUser caller, UUID fileId, Purpose purpose) {
        ArchivedFile file =
                files.findByIdAndOwnerId(fileId, caller.id())
                        .orElseThrow(ResourceNotFoundException::new);
        if (purpose == Purpose.PREVIEW && !Previews.isPreviewable(file.getMediaType())) {
            throw new PreviewNotAvailableException();
        }
        Instant now = Instant.now(clock);
        files.markAccessed(fileId, caller.id(), now);
        activity.record(
                fileId,
                caller.id(),
                purpose == Purpose.PREVIEW ? FileEventType.PREVIEWED : FileEventType.DOWNLOADED,
                Map.of());
        return tickets.issue(caller.id(), fileId, purpose);
    }

    /** Resolves a verified ticket. The file must still exist and belong to the ticket's user. */
    @Transactional(readOnly = true)
    public ServedContent open(VerifiedTicket ticket) {
        ArchivedFile file =
                files.findByIdAndOwnerId(ticket.fileId(), ticket.userId())
                        .orElseThrow(ResourceNotFoundException::new);
        Resource resource = blobs.resource(file.getStorageKey());
        if (!resource.exists()) {
            log.error("Blob missing for file {}", file.getId());
            throw new ResourceNotFoundException();
        }

        String filename = safeFilename(file.getDisplayName(), file.getOriginalFilename());
        Previews.Kind kind = Previews.kindOf(file.getMediaType());
        String etag = "\"" + file.getSha256() + "\"";

        if (ticket.purpose() == Purpose.PREVIEW && kind != null) {
            MediaType type =
                    switch (kind) {
                        // Text is always served as plain text so it can never render as HTML.
                        case TEXT -> new MediaType("text", "plain", StandardCharsets.UTF_8);
                        case IMAGE, PDF -> MediaType.parseMediaType(file.getMediaType());
                    };
            return new ServedContent(
                    resource,
                    type,
                    ContentDisposition.inline().filename(filename, StandardCharsets.UTF_8).build(),
                    etag,
                    kind == Previews.Kind.PDF ? CSP_PDF : CSP_STRICT);
        }
        return new ServedContent(
                resource,
                MediaType.APPLICATION_OCTET_STREAM,
                ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build(),
                etag,
                CSP_STRICT);
    }

    /**
     * The name offered to the browser: the display name, keeping the original extension if the
     * user's display name dropped it. Control characters, quotes and path separators are removed so
     * the value can never break the header.
     */
    static String safeFilename(String displayName, String originalFilename) {
        String name = displayName;
        int dot = originalFilename.lastIndexOf('.');
        if (dot > 0) {
            String extension = originalFilename.substring(dot);
            if (!name.toLowerCase(Locale.ROOT).endsWith(extension.toLowerCase(Locale.ROOT))) {
                name = name + extension;
            }
        }
        name = name.replaceAll("[\\p{Cntrl}\"\\\\/]", "_").strip();
        if (name.isEmpty()) {
            name = "download";
        }
        return name.length() > MAX_FILENAME_LENGTH ? name.substring(0, MAX_FILENAME_LENGTH) : name;
    }
}
