package app.trashbin.storage;

import java.nio.file.Path;

/**
 * An upload written to staging but not yet promoted. {@code sha256} and {@code sizeBytes} are
 * computed from the bytes actually received, never taken from the client.
 */
public record StagedBlob(Path path, long sizeBytes, String sha256) {}
