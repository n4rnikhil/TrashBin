package app.trashbin.archive.domain;

/** Progress of full-text extraction (implemented in M4; new files start PENDING). */
public enum ExtractionStatus {
    PENDING,
    DONE,
    SKIPPED,
    FAILED
}
