package app.trashbin.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Validates and prepares the storage root at startup so misconfiguration fails fast.
 *
 * <p>Layout: {@code <root>/staging} for in-flight uploads and {@code <root>/blobs} for final files.
 * Both must be on the same volume so the final move can be atomic (docs/SYSTEM_ARCHITECTURE.md
 * section 5).
 */
@Component
class StorageRootInitializer implements InitializingBean {

    static final String STAGING_DIR = "staging";
    static final String BLOBS_DIR = "blobs";

    private static final Logger log = LoggerFactory.getLogger(StorageRootInitializer.class);

    private final StorageProperties properties;

    StorageRootInitializer(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        Path root = Path.of(properties.root());
        if (!root.isAbsolute()) {
            throw new IllegalStateException("trashbin.storage.root must be an absolute path");
        }
        root = root.normalize();
        rejectInsideGitWorkingTree(root);

        try {
            Path staging = Files.createDirectories(root.resolve(STAGING_DIR));
            Path blobs = Files.createDirectories(root.resolve(BLOBS_DIR));
            if (!Files.isWritable(staging) || !Files.isWritable(blobs)) {
                throw new IllegalStateException("Storage root is not writable: " + root);
            }
            if (!Files.getFileStore(staging).equals(Files.getFileStore(blobs))) {
                throw new IllegalStateException(
                        "staging and blobs must be on the same volume for atomic moves");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot initialize storage root " + root, e);
        }
        log.info("Storage root ready at {}", root);
    }

    /** Application data must never live inside the source tree, where it could be committed. */
    private static void rejectInsideGitWorkingTree(Path root) {
        for (Path p = root; p != null; p = p.getParent()) {
            if (Files.exists(p.resolve(".git"))) {
                throw new IllegalStateException(
                        "trashbin.storage.root must be outside any git working tree, but "
                                + p
                                + " is a repository");
            }
        }
    }
}
