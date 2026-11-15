package app.trashbin.collection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Request and response shapes of the collection API. */
public final class CollectionDtos {

    private CollectionDtos() {}

    /** Compact reference, as shown on a file. */
    public record CollectionRef(UUID id, String name) {}

    public record CollectionSummary(
            UUID id, String name, String description, long fileCount, Instant updatedAt) {}

    public record CreateCollectionRequest(
            @NotBlank @Size(max = 100) String name, @Size(max = 2000) String description) {}

    /** Null fields are left unchanged; an empty description clears it. */
    public record UpdateCollectionRequest(
            @Size(min = 1, max = 100) String name, @Size(max = 2000) String description) {}
}
