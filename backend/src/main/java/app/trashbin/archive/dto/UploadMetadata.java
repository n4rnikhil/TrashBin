package app.trashbin.archive.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Optional user-supplied context sent as the {@code metadata} part of an upload. */
public record UploadMetadata(
        @Size(max = 255) String displayName,
        @Size(max = 10000) String notes,
        @Size(max = 2000) String contextNote,
        LocalDate originDate,
        Boolean allowDuplicate) {

    public static UploadMetadata empty() {
        return new UploadMetadata(null, null, null, null, false);
    }

    public boolean duplicatesAllowed() {
        return Boolean.TRUE.equals(allowDuplicate);
    }
}
