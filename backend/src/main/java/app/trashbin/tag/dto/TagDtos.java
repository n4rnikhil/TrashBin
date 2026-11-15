package app.trashbin.tag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request and response shapes of the tag API. */
public final class TagDtos {

    private TagDtos() {}

    /** Hex color like {@code #3366CC}; an empty string clears the color on update. */
    static final String COLOR = "^$|^#[0-9A-Fa-f]{6}$";

    /** Compact tag as shown on files. */
    public record TagRef(UUID id, String name, String color) {}

    /** A tag with the number of files that carry it. */
    public record TagSummary(UUID id, String name, String color, long fileCount) {}

    public record CreateTagRequest(
            @NotBlank @Size(max = 50) String name,
            @Pattern(regexp = COLOR, message = "must be a hex color like #3366CC") String color) {}

    /** Null fields are left unchanged. */
    public record UpdateTagRequest(
            @Size(min = 1, max = 50) String name,
            @Pattern(regexp = COLOR, message = "must be a hex color like #3366CC") String color) {}

    public record MergeTagRequest(@NotNull UUID targetTagId) {}
}
