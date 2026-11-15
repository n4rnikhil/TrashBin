package app.trashbin.archive.repository;

import java.util.UUID;

/** A file still waiting for text extraction. */
public record PendingExtraction(UUID fileId, UUID ownerId) {}
