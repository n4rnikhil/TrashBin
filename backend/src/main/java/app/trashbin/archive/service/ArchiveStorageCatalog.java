package app.trashbin.archive.service;

import app.trashbin.archive.repository.ArchivedFileRepository;
import app.trashbin.storage.StorageCatalog;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tells the storage reconciler which blobs the archive references. */
@Service
@Transactional(readOnly = true)
public class ArchiveStorageCatalog implements StorageCatalog {

    private final ArchivedFileRepository files;

    public ArchiveStorageCatalog(ArchivedFileRepository files) {
        this.files = files;
    }

    @Override
    public boolean isReferenced(UUID storageKey) {
        return files.existsByStorageKey(storageKey);
    }

    @Override
    public List<StoredFileRef> allReferences() {
        return files.findAllStorageRefs().stream()
                .map(ref -> new StoredFileRef(ref.fileId(), ref.storageKey()))
                .toList();
    }
}
