package app.trashbin.archive.web;

import app.trashbin.archive.service.AccessTicketService;
import app.trashbin.archive.service.AccessTicketService.Purpose;
import app.trashbin.archive.service.AccessTicketService.Ticket;
import app.trashbin.archive.service.ContentService;
import app.trashbin.archive.service.ContentService.ServedContent;
import app.trashbin.common.error.TicketInvalidException;
import app.trashbin.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Download and preview. Step 1 (authenticated): ask for a short-lived link. Step 2 (link only):
 * fetch the bytes — used directly by {@code <a>}, {@code <img>} and {@code <iframe>}.
 */
@RestController
public class ContentController {

    public record TicketRequest(@NotNull Purpose purpose) {}

    /** {@code url} is relative to the site, e.g. {@code /api/v1/content/…}. */
    public record AccessTicket(String url, Instant expiresAt) {}

    private final ContentService content;
    private final AccessTicketService tickets;

    public ContentController(ContentService content, AccessTicketService tickets) {
        this.content = content;
        this.tickets = tickets;
    }

    @PostMapping("/api/v1/files/{id}/access-tickets")
    public AccessTicket createTicket(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody TicketRequest request) {
        Ticket ticket = content.createTicket(AuthenticatedUser.from(jwt), id, request.purpose());
        return new AccessTicket("/api/v1/content/" + ticket.token(), ticket.expiresAt());
    }

    /**
     * Streams the file. Returning a {@link Resource} lets Spring answer {@code Range} requests
     * (206/416), which PDF viewers and resumable downloads rely on.
     */
    @GetMapping("/api/v1/content/{ticket}")
    public ResponseEntity<Resource> content(
            @PathVariable String ticket,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
                    String ifNoneMatch) {
        ServedContent served =
                content.open(tickets.verify(ticket).orElseThrow(TicketInvalidException::new));
        if (served.etag().equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(served.etag()).build();
        }
        return ResponseEntity.ok()
                .contentType(served.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, served.disposition().toString())
                .header("Content-Security-Policy", served.contentSecurityPolicy())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .eTag(served.etag())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(served.resource());
    }
}
