package app.trashbin.archive.service;

import app.trashbin.archive.domain.ArchivedFile;
import app.trashbin.archive.domain.Extractable;
import app.trashbin.archive.domain.ExtractionStatus;
import app.trashbin.archive.repository.ArchivedFileRepository;
import app.trashbin.archive.repository.PendingExtraction;
import app.trashbin.config.ExtractionProperties;
import app.trashbin.content.TextExtractor;
import app.trashbin.content.TextExtractor.Extracted;
import app.trashbin.storage.BlobStore;
import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Extracts text from uploaded documents so search can look inside them (FR-F5).
 *
 * <p>Runs off the request path: right after an upload commits, and in a periodic sweep that also
 * catches anything missed (restarts, a full queue, files from before this feature). Guard rails: a
 * type allowlist, an input-size cap, a character cap, a per-file timeout and failure isolation —
 * one bad file only ends up FAILED. Status updates never change a file's version.
 */
@Service
public class TextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(TextExtractionService.class);

    private final ArchivedFileRepository files;
    private final BlobStore blobs;
    private final TextExtractor extractor;
    private final ExtractionWriter writer;
    private final ExtractionProperties properties;
    private final Clock clock;

    /** Files currently being processed, so the sweep and the upload trigger never double up. */
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    /** One worker for queued files; a bounded queue so bursts degrade to the sweep. */
    private final ThreadPoolExecutor worker =
            new ThreadPoolExecutor(
                    1,
                    1,
                    0,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(500),
                    daemon("extract"));

    /** Parsing runs here so it can be abandoned after the timeout. */
    private final ExecutorService parsers = Executors.newFixedThreadPool(2, daemon("tika"));

    public TextExtractionService(
            ArchivedFileRepository files,
            BlobStore blobs,
            TextExtractor extractor,
            ExtractionWriter writer,
            ExtractionProperties properties,
            Clock clock) {
        this.files = files;
        this.blobs = blobs;
        this.extractor = extractor;
        this.writer = writer;
        this.properties = properties;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFileArchived(FileArchivedEvent event) {
        if (!properties.background()) {
            return;
        }
        try {
            worker.execute(() -> extract(event.ownerId(), event.fileId()));
        } catch (RejectedExecutionException e) {
            log.info(
                    "Extraction queue full; file {} will be handled by the next sweep",
                    event.fileId());
        }
    }

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "PT1M")
    public void scheduledSweep() {
        if (properties.background()) {
            processPending();
        }
    }

    /** Extracts up to one batch of pending files synchronously. Returns how many were handled. */
    public int processPending() {
        List<PendingExtraction> pending =
                files.findByExtractionStatus(
                        ExtractionStatus.PENDING, PageRequest.of(0, properties.batchSize()));
        pending.forEach(p -> extract(p.ownerId(), p.fileId()));
        return pending.size();
    }

    public void extract(UUID ownerId, UUID fileId) {
        if (!inFlight.add(fileId)) {
            return;
        }
        try {
            Optional<ArchivedFile> found = files.findByIdAndOwnerId(fileId, ownerId);
            if (found.isEmpty() || found.get().getExtractionStatus() != ExtractionStatus.PENDING) {
                return;
            }
            writer.markStatus(fileId, ownerId, run(found.get()));
        } finally {
            inFlight.remove(fileId);
        }
    }

    /** Returns the final status; on success the text has already been stored with DONE. */
    private ExtractionStatus run(ArchivedFile file) {
        if (!Extractable.isExtractable(file.getMediaType())
                || file.getSizeBytes() > properties.maxInput().toBytes()) {
            return ExtractionStatus.SKIPPED;
        }
        Future<Extracted> parse = null;
        try (InputStream in = blobs.open(file.getStorageKey())) {
            parse =
                    parsers.submit(
                            () ->
                                    extractor.extract(
                                            in, file.getMediaType(), properties.maxChars()));
            Extracted result = parse.get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
            writer.storeText(
                    file.getId(),
                    file.getOwnerId(),
                    result.text(),
                    result.truncated(),
                    Instant.now(clock));
            log.info("Extracted {} characters from file {}", result.text().length(), file.getId());
            return ExtractionStatus.DONE;
        } catch (TimeoutException e) {
            parse.cancel(true);
            log.warn("Text extraction timed out for file {}", file.getId());
            return ExtractionStatus.FAILED;
        } catch (ExecutionException e) {
            log.warn(
                    "Text extraction failed for file {}: {}",
                    file.getId(),
                    e.getCause() == null ? "unknown" : e.getCause().getClass().getSimpleName());
            return ExtractionStatus.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ExtractionStatus.PENDING; // shutting down; retried by the next sweep
        } catch (java.io.IOException | UncheckedIOException e) {
            log.warn("Could not read blob of file {} for extraction", file.getId(), e);
            return ExtractionStatus.FAILED;
        }
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
        parsers.shutdownNow();
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, "trashbin-" + name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
