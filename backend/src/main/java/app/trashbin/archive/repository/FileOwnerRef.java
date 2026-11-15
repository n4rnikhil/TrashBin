package app.trashbin.archive.repository;

import java.util.UUID;

/** A file together with its owner, for background work that must stay owner-scoped. */
public record FileOwnerRef(UUID fileId, UUID ownerId) {}
