package app.trashbin.archive.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Request bodies for editing a file's metadata. */
public final class FileEditRequests {

    private FileEditRequests() {}

    /**
     * Partial update. A {@code null} field is left unchanged; for {@code notes}, {@code
     * contextNote} and {@code originDate} an empty string clears the value.
     */
    public record UpdateFileRequest(
            @Size(min = 1, max = 255) String displayName,
            @Size(max = 10000) String notes,
            @Size(max = 2000) String contextNote,
            @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "must be a date (YYYY-MM-DD)")
                    String originDate) {}

    /** The complete new tag set (unknown names are created). */
    public record ReplaceTagsRequest(@NotNull @Size(max = 20) List<@NotNull String> tags) {}

    public record FavoriteRequest(@NotNull Boolean favorite) {}
}
