package app.trashbin.archive.dto;

import app.trashbin.tag.dto.TagDtos.TagRef;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * List view of a file. {@code typeGroup} and {@code status} carry the enum names documented in
 * docs/API_CONTRACT.md. The storage key and owner are never exposed.
 */
public record FileSummary(
        UUID id,
        String displayName,
        String mediaType,
        String typeGroup,
        long sizeBytes,
        String status,
        boolean favorite,
        Instant archivedAt,
        LocalDate originDate,
        List<TagRef> tags) {}
