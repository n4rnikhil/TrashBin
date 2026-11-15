package app.trashbin.archive.service;

import java.nio.file.Path;

/**
 * Extension point for content scanning (for example a local malware scanner). v1 ships a no-op
 * implementation; a rejecting scanner throws {@link UploadExceptions.UnsupportedFileTypeException}.
 */
public interface UploadScanner {

    void scan(Path stagedFile, String mediaType);
}
