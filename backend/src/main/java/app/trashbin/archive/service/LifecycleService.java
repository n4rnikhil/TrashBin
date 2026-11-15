package app.trashbin.archive.service;

import app.trashbin.activity.domain.FileEventType;
import app.trashbin.activity.service.ActivityService;
import app.trashbin.archive.domain.ArchivedFile;
import app.trashbin.archive.domain.FileStatus;
import app.trashbin.archive.dto.FileDetail;
import app.trashbin.archive.repository.ArchivedFileRepository;
import app.trashbin.archive.repository.FileOwnerRef;
import app.trashbin.common.error.InvalidStateTransitionException;
import app.trashbin.common.error.ResourceNotFoundException;
import app.trashbin.common.security.AuthenticatedUser;
import app.trashbin.config.LifecycleProperties;
import app.trashbin.storage.BlobStore;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The "nothing is trash until you decide it is" lifecycle (FR-L1 – FR-L4, FR-F6): ARCHIVED →
 * DISCARDED (restorable) → PURGED (row and bytes gone).
 */
@Service
public class LifecycleService {

    /** Outcome of re-hashing the stored bytes. {@code actualSha256} is null if the blob is gone. */
    public record IntegrityResult(
            boolean ok, String expectedSha256, String actualSha256, Instant verifiedAt) {}

    private static final Logger log = LoggerFactory.getLogger(LifecycleService.class);

    private final ArchivedFileRepository files;
    private final BlobStore blobs;
    private final ActivityService activity;
    private final ArchiveQueryService queries;
    private final LifecycleProperties properties;
    private final Clock clock;

    public LifecycleService(
            ArchivedFileRepository files,
            BlobStore blobs,
            ActivityService activity,
            ArchiveQueryService queries,
            LifecycleProperties properties,
            Clock clock) {
        this.files = files;
        this.blobs = blobs;
        this.activity = activity;
        this.queries = queries;
        this.properties = properties;
        this.clock = clock;
    }

    /** When a file discarded at {@code now} will be purged; null if automatic purge is off. */
    static Instant purgeAfter(Instant now, int retentionDays) {
        return retentionDays == 0 ? null : now.plus(Duration.ofDays(retentionDays));
    }

    @Transactional
    public FileDetail discard(AuthenticatedUser caller, UUID fileId) {
        ArchivedFile file = require(caller, fileId);
        Instant now = Instant.now(clock);
        if (!file.discard(now, purgeAfter(now, properties.retentionDays()))) {
            throw new InvalidStateTransitionException("This file is already discarded.");
        }
        files.saveAndFlush(file);
        activity.record(fileId, caller.id(), FileEventType.DISCARDED, Map.of());
        return queries.get(caller, fileId);
    }

    @Transactional
    public FileDetail restore(AuthenticatedUser caller, UUID fileId) {
        ArchivedFile file = require(caller, fileId);
        if (!file.restore(Instant.now(clock))) {
            throw new InvalidStateTransitionException("Only discarded files can be restored.");
        }
        files.saveAndFlush(file);
        activity.record(fileId, caller.id(), FileEventType.RESTORED, Map.of());
        return queries.get(caller, fileId);
    }

    /**
     * Permanently deletes a discarded file. Only discarded files can be purged, so a single click
     * can never destroy an archived file.
     */
    @Transactional
    public void purge(AuthenticatedUser caller, UUID fileId) {
        ArchivedFile file = require(caller, fileId);
        if (file.getStatus() != FileStatus.DISCARDED) {
            throw new InvalidStateTransitionException(
                    "Discard the file before deleting it permanently.");
        }
        delete(file);
    }

    /** Purges discarded files whose grace period has ended. Returns how many were purged. */
    @Transactional
    public int purgeExpired(int batchSize) {
        List<FileOwnerRef> due =
                files.findPurgeDue(
                        FileStatus.DISCARDED, Instant.now(clock), PageRequest.of(0, batchSize));
        for (FileOwnerRef ref : due) {
            files.findByIdAndOwnerId(ref.fileId(), ref.ownerId()).ifPresent(this::delete);
        }
        return due.size();
    }

    /** Re-hashes the stored bytes and compares them with the checksum taken at upload. */
    @Transactional
    public IntegrityResult verify(AuthenticatedUser caller, UUID fileId) {
        ArchivedFile file = require(caller, fileId);
        String actual = hash(file);
        boolean ok = file.getSha256().equals(actual);
        if (!ok) {
            log.error("Integrity check failed for file {}", fileId);
        }
        activity.record(fileId, caller.id(), FileEventType.INTEGRITY_VERIFIED, Map.of("ok", ok));
        return new IntegrityResult(ok, file.getSha256(), actual, Instant.now(clock));
    }

    /**
     * Deletes the row (tags, collections, text and events cascade) and, only once that has
     * committed, the bytes. If deleting the bytes fails the storage reconciler removes them later.
     */
    private void delete(ArchivedFile file) {
        UUID storageKey = file.getStorageKey();
        files.delete(file);
        files.flush();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            blobs.delete(storageKey);
                        } catch (UncheckedIOException e) {
                            log.warn("Could not delete purged blob; the reconciler will retry", e);
                        }
                    }
                });
        log.info("Purged file {}", file.getId());
    }

    private String hash(ArchivedFile file) {
        if (!blobs.exists(file.getStorageKey())) {
            return null;
        }
        try (InputStream in = blobs.open(file.getStorageKey())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the stored file", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    private ArchivedFile require(AuthenticatedUser caller, UUID fileId) {
        return files.findByIdAndOwnerId(fileId, caller.id())
                .orElseThrow(ResourceNotFoundException::new);
    }
}
