package app.trashbin.tag.web;

import app.trashbin.common.security.AuthenticatedUser;
import app.trashbin.tag.dto.TagDtos.CreateTagRequest;
import app.trashbin.tag.dto.TagDtos.MergeTagRequest;
import app.trashbin.tag.dto.TagDtos.TagSummary;
import app.trashbin.tag.dto.TagDtos.UpdateTagRequest;
import app.trashbin.tag.service.TagService;
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

@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private final TagService tags;

    public TagController(TagService tags) {
        this.tags = tags;
    }

    @GetMapping
    public List<TagSummary> list(@AuthenticationPrincipal Jwt jwt) {
        return tags.list(AuthenticatedUser.from(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TagSummary create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateTagRequest request) {
        return tags.create(AuthenticatedUser.from(jwt), request.name(), request.color());
    }

    @PatchMapping("/{id}")
    public TagSummary update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTagRequest request) {
        return tags.update(AuthenticatedUser.from(jwt), id, request.name(), request.color());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        tags.delete(AuthenticatedUser.from(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/merge")
    public TagSummary merge(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody MergeTagRequest request) {
        return tags.merge(AuthenticatedUser.from(jwt), id, request.targetTagId());
    }
}
