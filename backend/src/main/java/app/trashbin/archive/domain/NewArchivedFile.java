package app.trashbin.archive.domain;

import java.time.LocalDate;
import java.util.UUID;

/** Everything needed to create an {@link ArchivedFile}; keeps the constructor readable. */
public record NewArchivedFile(
        UUID ownerId,
        String originalFilename,
        String displayName,
        String mediaType,
        String declaredMediaType,
        TypeGroup typeGroup,
        long sizeBytes,
        String sha256,
        UUID storageKey,
        String notes,
        String contextNote,
        LocalDate originDate) {}
