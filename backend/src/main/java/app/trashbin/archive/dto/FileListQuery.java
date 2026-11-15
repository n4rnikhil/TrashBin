package app.trashbin.archive.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Parameters of {@code GET /api/v1/files} as received; validated by the query service.
 *
 * @param from inclusive archive date (UTC)
 * @param to inclusive archive date (UTC)
 * @param sort {@code field,dir}; null means relevance when {@code q} is given, else newest first
 */
public record FileListQuery(
        String q,
        String status,
        Boolean favorite,
        String typeGroup,
        List<UUID> tagIds,
        UUID collectionId,
        LocalDate from,
        LocalDate to,
        String sort,
        int page,
        int size) {}
