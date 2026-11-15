package app.trashbin.archive.domain;

/** Lifecycle state. A purged file has no row, so PURGED is not a value. */
public enum FileStatus {
    ARCHIVED,
    DISCARDED
}
