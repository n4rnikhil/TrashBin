package app.trashbin.storage;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;

/**
 * Port for binary storage. Blobs are addressed only by a server-generated UUID storage key; user
 * file names never reach this interface (docs/SYSTEM_ARCHITECTURE.md section 5).
 */
public interface BlobStore {

    /**
     * Streams {@code content} into staging while counting bytes and hashing them.
     *
     * @throws app.trashbin.common.error.FileTooLargeException when more than {@code maxBytes}
     *     arrive; the partial file is removed
     */
    StagedBlob stage(InputStream content, long maxBytes);

    /** Atomically moves a staged blob to its final location under {@code storageKey}. */
    void promote(StagedBlob staged, UUID storageKey);

    /** Removes a staged blob that was not promoted. Safe to call more than once. */
    void discard(StagedBlob staged);

    InputStream open(UUID storageKey);

    /** Random-access view of a blob, used to serve HTTP Range requests. */
    Resource resource(UUID storageKey);

    boolean exists(UUID storageKey);

    /** Deletes a blob if present. Returns whether a file was removed. */
    boolean delete(UUID storageKey);

    /** Every stored blob, for reconciliation. */
    List<BlobInfo> listBlobs();

    /** Removes abandoned staging files last modified before {@code cutoff}. */
    int deleteStagedOlderThan(Instant cutoff);

    record BlobInfo(UUID storageKey, Instant lastModified) {}
}
