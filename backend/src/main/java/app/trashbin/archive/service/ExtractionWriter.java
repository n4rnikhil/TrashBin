package app.trashbin.archive.service;

import app.trashbin.archive.domain.ExtractionStatus;
import app.trashbin.archive.repository.ArchivedFileRepository;
import app.trashbin.archive.repository.FileTextDao;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Short transactions for extraction results; parsing itself never holds a transaction. */
@Service
@Transactional
public class ExtractionWriter {

    private final ArchivedFileRepository files;
    private final FileTextDao texts;

    public ExtractionWriter(ArchivedFileRepository files, FileTextDao texts) {
        this.files = files;
        this.texts = texts;
    }

    public void markStatus(UUID fileId, UUID ownerId, ExtractionStatus status) {
        files.setExtractionStatus(fileId, ownerId, status);
    }

    /** Text and DONE commit together; a file that vanished meanwhile is left alone. */
    public void storeText(
            UUID fileId, UUID ownerId, String text, boolean truncated, Instant extractedAt) {
        if (files.setExtractionStatus(fileId, ownerId, ExtractionStatus.DONE) == 1) {
            texts.save(fileId, text, truncated, extractedAt);
        }
    }
}
