package app.trashbin.archive.repository;

import java.util.UUID;

/** Projection used by storage reconciliation. */
public record StorageRef(UUID fileId, UUID storageKey) {}
