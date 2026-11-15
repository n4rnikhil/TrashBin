package app.trashbin.collection.web;

import app.trashbin.collection.dto.CollectionDtos.CollectionSummary;
import app.trashbin.collection.dto.CollectionDtos.CreateCollectionRequest;
import app.trashbin.collection.dto.CollectionDtos.UpdateCollectionRequest;
import app.trashbin.collection.service.CollectionService;
import app.trashbin.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Collections. Membership is managed per file: {@code PUT /api/v1/files/{id}/collections}. */
@RestController
@RequestMapping("/api/v1/collections")
public class CollectionController {

    private final CollectionService collections;

    public CollectionController(CollectionService collections) {
        this.collections = collections;
    }

    @GetMapping
    public List<CollectionSummary> list(@AuthenticationPrincipal Jwt jwt) {
        return collections.list(AuthenticatedUser.from(jwt));
    }

    @GetMapping("/{id}")
    public CollectionSummary get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return collections.get(AuthenticatedUser.from(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionSummary create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateCollectionRequest request) {
        return collections.create(
                AuthenticatedUser.from(jwt), request.name(), request.description());
    }

    @PatchMapping("/{id}")
    public CollectionSummary update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCollectionRequest request) {
        return collections.update(
                AuthenticatedUser.from(jwt), id, request.name(), request.description());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        collections.delete(AuthenticatedUser.from(jwt), id);
        return ResponseEntity.noContent().build();
    }
}
