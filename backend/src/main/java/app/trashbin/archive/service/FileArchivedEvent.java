package app.trashbin.archive.service;

import java.util.UUID;

/** Published when an upload's transaction is about to commit. */
public record FileArchivedEvent(UUID ownerId, UUID fileId) {}
