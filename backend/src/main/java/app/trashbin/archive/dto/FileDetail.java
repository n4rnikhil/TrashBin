package app.trashbin.archive.dto;

import app.trashbin.collection.dto.CollectionDtos.CollectionRef;
import app.trashbin.tag.dto.TagDtos.TagRef;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Full metadata of one file. {@code version} is also sent as the ETag for If-Match edits. */
public record FileDetail(
        UUID id,
        String displayName,
        String originalFilename,
        String mediaType,
        String typeGroup,
        long sizeBytes,
        String sha256,
        String status,
        boolean favorite,
        boolean previewable,
        String extractionStatus,
        String notes,
        String contextNote,
        LocalDate originDate,
        List<TagRef> tags,
        List<CollectionRef> collections,
        Instant archivedAt,
        Instant lastAccessedAt,
        Instant discardedAt,
        Instant purgeAfter,
        Instant updatedAt,
        long version) {}
