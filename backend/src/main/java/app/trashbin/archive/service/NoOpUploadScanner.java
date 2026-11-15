package app.trashbin.archive.service;

import java.nio.file.Path;
import org.springframework.stereotype.Component;

/** Accepts everything. Replace with a real scanner bean to enable scanning. */
@Component
public class NoOpUploadScanner implements UploadScanner {

    @Override
    public void scan(Path stagedFile, String mediaType) {
        // Intentionally empty: no scanner is configured in v1.
    }
}
