package app.trashbin.archive.web;

import app.trashbin.archive.dto.FileDetail;
import app.trashbin.archive.dto.FileEditRequests.FavoriteRequest;
import app.trashbin.archive.dto.FileEditRequests.ReplaceCollectionsRequest;
import app.trashbin.archive.dto.FileEditRequests.ReplaceTagsRequest;
import app.trashbin.archive.dto.FileEditRequests.UpdateFileRequest;
import app.trashbin.archive.dto.FileListQuery;
import app.trashbin.archive.dto.FileSummary;
import app.trashbin.archive.dto.IncomingFile;
import app.trashbin.archive.dto.UploadMetadata;
import app.trashbin.archive.service.ArchiveEditService;
import app.trashbin.archive.service.ArchiveQueryService;
import app.trashbin.archive.service.UploadService;
import app.trashbin.common.dto.PageResponse;
import app.trashbin.common.error.InvalidRequestException;
import app.trashbin.common.error.PreconditionRequiredException;
import app.trashbin.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    /** Accepts {@code "3"} and weak {@code W/"3"} entity tags. */
    private static final Pattern VERSION_ETAG = Pattern.compile("^(?:W/)?\"(\\d{1,18})\"$");

    private final UploadService uploads;
    private final ArchiveQueryService queries;
    private final ArchiveEditService edits;

    public FileController(
            UploadService uploads, ArchiveQueryService queries, ArchiveEditService edits) {
        this.uploads = uploads;
        this.queries = queries;
        this.edits = edits;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileDetail upload(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart(value = "metadata", required = false) UploadMetadata metadata) {
        IncomingFile incoming =
                new IncomingFile(
                        file.getOriginalFilename(), file.getContentType(), file.getSize(), file);
        return uploads.upload(AuthenticatedUser.from(jwt), incoming, metadata);
    }

    /** List, search and filter (docs/API_CONTRACT.md 3.2). */
    @GetMapping
    public PageResponse<FileSummary> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "ARCHIVED") String status,
            @RequestParam(required = false) Boolean favorite,
            @RequestParam(required = false) String typeGroup,
            @RequestParam(name = "tag", required = false) List<UUID> tagIds,
            @RequestParam(name = "collection", required = false) UUID collectionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return queries.list(
                AuthenticatedUser.from(jwt),
                new FileListQuery(
                        q,
                        status,
                        favorite,
                        typeGroup,
                        tagIds,
                        collectionId,
                        from,
                        to,
                        sort,
                        page,
                        size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FileDetail> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return withETag(queries.get(AuthenticatedUser.from(jwt), id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<FileDetail> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody UpdateFileRequest request) {
        return withETag(
                edits.update(AuthenticatedUser.from(jwt), id, parseVersion(ifMatch), request));
    }

    @PutMapping("/{id}/tags")
    public ResponseEntity<FileDetail> replaceTags(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ReplaceTagsRequest request) {
        return withETag(edits.replaceTags(AuthenticatedUser.from(jwt), id, request.tags()));
    }

    @PutMapping("/{id}/collections")
    public ResponseEntity<FileDetail> replaceCollections(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody ReplaceCollectionsRequest request) {
        return withETag(
                edits.replaceCollections(AuthenticatedUser.from(jwt), id, request.collectionIds()));
    }

    /** Idempotent flag; no If-Match needed because there is nothing to lose. */
    @PutMapping("/{id}/favorite")
    public ResponseEntity<FileDetail> favorite(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody FavoriteRequest request) {
        return withETag(edits.setFavorite(AuthenticatedUser.from(jwt), id, request.favorite()));
    }

    private static ResponseEntity<FileDetail> withETag(FileDetail detail) {
        return ResponseEntity.ok().eTag("\"" + detail.version() + "\"").body(detail);
    }

    private static long parseVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException();
        }
        Matcher matcher = VERSION_ETAG.matcher(ifMatch.strip());
        if (!matcher.matches()) {
            throw new InvalidRequestException("If-Match must be the ETag of the file, e.g. \"3\".");
        }
        return Long.parseLong(matcher.group(1));
    }
}
