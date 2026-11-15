package app.trashbin.archive.web;

import app.trashbin.activity.dto.FileEventView;
import app.trashbin.archive.dto.FileDetail;
import app.trashbin.archive.service.ArchiveQueryService;
import app.trashbin.archive.service.DiscoveryService;
import app.trashbin.archive.service.DiscoveryService.Stats;
import app.trashbin.archive.service.LifecycleService;
import app.trashbin.archive.service.LifecycleService.IntegrityResult;
import app.trashbin.common.dto.PageResponse;
import app.trashbin.common.security.AuthenticatedUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Discard, restore, purge, integrity, timeline, rediscovery and statistics. */
@RestController
public class FileLifecycleController {

    private final LifecycleService lifecycle;
    private final ArchiveQueryService queries;
    private final DiscoveryService discovery;

    public FileLifecycleController(
            LifecycleService lifecycle, ArchiveQueryService queries, DiscoveryService discovery) {
        this.lifecycle = lifecycle;
        this.queries = queries;
        this.discovery = discovery;
    }

    @PostMapping("/api/v1/files/{id}/discard")
    public ResponseEntity<FileDetail> discard(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return withETag(lifecycle.discard(AuthenticatedUser.from(jwt), id));
    }

    @PostMapping("/api/v1/files/{id}/restore")
    public ResponseEntity<FileDetail> restore(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return withETag(lifecycle.restore(AuthenticatedUser.from(jwt), id));
    }

    /** Permanent. Only allowed for discarded files (409 otherwise). */
    @DeleteMapping("/api/v1/files/{id}")
    public ResponseEntity<Void> purge(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        lifecycle.purge(AuthenticatedUser.from(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/files/{id}/verify")
    public IntegrityResult verify(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return lifecycle.verify(AuthenticatedUser.from(jwt), id);
    }

    @GetMapping("/api/v1/files/{id}/events")
    public PageResponse<FileEventView> events(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queries.timeline(AuthenticatedUser.from(jwt), id, page, size);
    }

    @GetMapping("/api/v1/rediscover")
    public List<app.trashbin.archive.dto.FileSummary> rediscover(
            @AuthenticationPrincipal Jwt jwt, @RequestParam String mode) {
        return discovery.rediscover(AuthenticatedUser.from(jwt), mode);
    }

    @GetMapping("/api/v1/stats")
    public Stats stats(@AuthenticationPrincipal Jwt jwt) {
        return discovery.stats(AuthenticatedUser.from(jwt));
    }

    private static ResponseEntity<FileDetail> withETag(FileDetail detail) {
        return ResponseEntity.ok().eTag("\"" + detail.version() + "\"").body(detail);
    }
}
