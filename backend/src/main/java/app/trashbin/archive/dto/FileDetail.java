package app.trashbin.archive.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Full metadata of one file. */
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
        String notes,
        String contextNote,
        LocalDate originDate,
        Instant archivedAt,
        Instant lastAccessedAt,
        Instant discardedAt,
        Instant purgeAfter,
        Instant updatedAt,
        long version) {}
