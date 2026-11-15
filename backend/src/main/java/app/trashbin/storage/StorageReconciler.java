package app.trashbin.storage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Repairs the database/filesystem gap (docs/SYSTEM_ARCHITECTURE.md section 5): removes abandoned
 * staging files and blobs no row references, and reports rows whose blob is missing. Missing blobs
 * mean data loss and are never "fixed" automatically.
 */
@Service
public class StorageReconciler {

    /**
     * Grace period so in-flight uploads (blob promoted, row not yet committed) are never touched.
     */
    static final Duration MIN_AGE = Duration.ofHours(1);

    private static final Logger log = LoggerFactory.getLogger(StorageReconciler.class);

    private final BlobStore blobStore;
    private final StorageCatalog catalog;
    private final Clock clock;

    public StorageReconciler(BlobStore blobStore, StorageCatalog catalog, Clock clock) {
        this.blobStore = blobStore;
        this.catalog = catalog;
        this.clock = clock;
    }

    public record Report(int stagingFilesRemoved, int orphanBlobsRemoved, int missingBlobs) {}

    @Scheduled(cron = "0 15 * * * *")
    public void scheduledRun() {
        reconcile();
    }

    public synchronized Report reconcile() {
        Instant cutoff = Instant.now(clock).minus(MIN_AGE);
        int staging = blobStore.deleteStagedOlderThan(cutoff);

        int orphans = 0;
        for (BlobStore.BlobInfo blob : blobStore.listBlobs()) {
            if (blob.lastModified().isBefore(cutoff)
                    && !catalog.isReferenced(blob.storageKey())
                    && blobStore.delete(blob.storageKey())) {
                orphans++;
            }
        }

        List<UUID> missing =
                catalog.allReferences().stream()
                        .filter(ref -> !blobStore.exists(ref.storageKey()))
                        .map(StorageCatalog.StoredFileRef::fileId)
                        .toList();
        if (!missing.isEmpty()) {
            log.error("Blobs missing for {} file(s): {}", missing.size(), missing);
        }
        Report report = new Report(staging, orphans, missing.size());
        log.info(
                "Storage reconciled: {} staging file(s) and {} orphan blob(s) removed, {} missing",
                staging,
                orphans,
                missing.size());
        return report;
    }
}
