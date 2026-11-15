package app.trashbin.archive.web;

import app.trashbin.archive.dto.FileDetail;
import app.trashbin.archive.dto.FileSummary;
import app.trashbin.archive.dto.IncomingFile;
import app.trashbin.archive.dto.UploadMetadata;
import app.trashbin.archive.service.ArchiveQueryService;
import app.trashbin.archive.service.UploadService;
import app.trashbin.common.dto.PageResponse;
import app.trashbin.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final UploadService uploads;
    private final ArchiveQueryService queries;

    public FileController(UploadService uploads, ArchiveQueryService queries) {
        this.uploads = uploads;
        this.queries = queries;
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

    @GetMapping
    public PageResponse<FileSummary> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "ARCHIVED") String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "archivedAt,desc") String sort) {
        return queries.list(AuthenticatedUser.from(jwt), status, page, size, sort);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FileDetail> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        FileDetail detail = queries.get(AuthenticatedUser.from(jwt), id);
        return ResponseEntity.ok().eTag("\"" + detail.version() + "\"").body(detail);
    }
}
