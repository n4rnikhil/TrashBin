package app.trashbin.storage;

import java.util.List;
import java.util.UUID;

/**
 * What the database knows about stored blobs. Implemented by the archive module so storage never
 * depends on it (dependency inversion).
 */
public interface StorageCatalog {

    boolean isReferenced(UUID storageKey);

    List<StoredFileRef> allReferences();

    /** A file row and the blob it points to. */
    record StoredFileRef(UUID fileId, UUID storageKey) {}
}
