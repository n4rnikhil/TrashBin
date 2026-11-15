package app.trashbin.archive.service;

import app.trashbin.archive.domain.NewArchivedFile;
import app.trashbin.archive.domain.TypeGroup;
import app.trashbin.archive.dto.FileDetail;
import app.trashbin.archive.dto.IncomingFile;
import app.trashbin.archive.dto.UploadMetadata;
import app.trashbin.common.error.FileTooLargeException;
import app.trashbin.common.security.AuthenticatedUser;
import app.trashbin.config.UploadProperties;
import app.trashbin.content.MediaTypeDetector;
import app.trashbin.content.MediaTypeDetector.Detection;
import app.trashbin.storage.BlobStore;
import app.trashbin.storage.StagedBlob;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Upload pipeline (docs/SYSTEM_ARCHITECTURE.md 4.2). The blob is moved into place before the
 * database commit; if the commit fails the blob is deleted again, and anything left behind by a
 * crash is removed by the storage reconciler. No transaction is held while bytes are copied.
 */
@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);
    private static final int MAX_DECLARED_TYPE_LENGTH = 255;

    private final BlobStore blobStore;
    private final MediaTypeDetector detector;
    private final UploadPolicy policy;
    private final UploadScanner scanner;
    private final ArchiveWriter writer;
    private final UploadProperties properties;

    public UploadService(
            BlobStore blobStore,
            MediaTypeDetector detector,
            UploadPolicy policy,
            UploadScanner scanner,
            ArchiveWriter writer,
            UploadProperties properties) {
        this.blobStore = blobStore;
        this.detector = detector;
        this.policy = policy;
        this.scanner = scanner;
        this.writer = writer;
        this.properties = properties;
    }

    public FileDetail upload(AuthenticatedUser caller, IncomingFile file, UploadMetadata metadata) {
        UploadMetadata meta = metadata == null ? UploadMetadata.empty() : metadata;
        String fileName = UploadPolicy.sanitizeFileName(file.originalName());
        long maxBytes = properties.maxFileSize().toBytes();
        if (file.declaredSize() > maxBytes) {
            throw new FileTooLargeException(maxBytes);
        }
        policy.checkExtensionAllowed(fileName);

        StagedBlob staged = stage(file, maxBytes);
        UUID storageKey = null;
        try {
            Detection detection = detector.detect(staged.path(), fileName);
            policy.checkSignature(fileName, detection);
            scanner.scan(staged.path(), detection.effective());

            storageKey = UUID.randomUUID();
            blobStore.promote(staged, storageKey);
            FileDetail detail =
                    writer.persist(
                            spec(caller, fileName, file, meta, detection, staged, storageKey),
                            meta.duplicatesAllowed(),
                            meta.tagNames());
            log.info("Archived file {} ({} bytes)", detail.id(), detail.sizeBytes());
            return detail;
        } catch (RuntimeException e) {
            if (storageKey != null) {
                compensate(storageKey);
            }
            throw e;
        } finally {
            blobStore.discard(staged); // no-op once promoted
        }
    }

    private StagedBlob stage(IncomingFile file, long maxBytes) {
        try (InputStream in = file.content().getInputStream()) {
            return blobStore.stage(in, maxBytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
    }

    private void compensate(UUID storageKey) {
        try {
            blobStore.delete(storageKey);
        } catch (UncheckedIOException e) {
            log.warn("Could not remove blob of a failed upload; the reconciler will retry", e);
        }
    }

    private static NewArchivedFile spec(
            AuthenticatedUser caller,
            String fileName,
            IncomingFile file,
            UploadMetadata meta,
            Detection detection,
            StagedBlob staged,
            UUID storageKey) {
        String displayName = blankToNull(meta.displayName());
        return new NewArchivedFile(
                caller.id(),
                fileName,
                displayName == null ? fileName : displayName,
                detection.effective(),
                truncate(file.declaredType()),
                TypeGroup.of(detection.effective()),
                staged.sizeBytes(),
                staged.sha256(),
                storageKey,
                blankToNull(meta.notes()),
                blankToNull(meta.contextNote()),
                meta.originDate());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String truncate(String declared) {
        if (declared == null) {
            return null;
        }
        return declared.length() > MAX_DECLARED_TYPE_LENGTH
                ? declared.substring(0, MAX_DECLARED_TYPE_LENGTH)
                : declared;
    }
}
