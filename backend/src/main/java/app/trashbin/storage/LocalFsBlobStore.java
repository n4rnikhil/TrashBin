package app.trashbin.storage;

import app.trashbin.common.error.FileTooLargeException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Local-filesystem blob storage: {@code <root>/staging} for in-flight uploads and {@code
 * <root>/blobs/ab/cd/<uuid>} for stored files. Every path is derived from a UUID and verified to
 * stay inside the root; symbolic links are never followed.
 */
@Component
public class LocalFsBlobStore implements BlobStore {

    private static final Logger log = LoggerFactory.getLogger(LocalFsBlobStore.class);
    private static final int BUFFER_SIZE = 64 * 1024;

    private final Path staging;
    private final Path blobs;

    public LocalFsBlobStore(StorageProperties properties) {
        Path root = Path.of(properties.root()).toAbsolutePath().normalize();
        this.staging = root.resolve(StorageRootInitializer.STAGING_DIR);
        this.blobs = root.resolve(StorageRootInitializer.BLOBS_DIR);
    }

    @Override
    public StagedBlob stage(InputStream content, long maxBytes) {
        Path temp = null;
        try {
            temp = Files.createTempFile(staging, "upload-", ".part");
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            long total = 0;
            byte[] buffer = new byte[BUFFER_SIZE];
            try (OutputStream out = Files.newOutputStream(temp, StandardOpenOption.WRITE)) {
                int read;
                while ((read = content.read(buffer)) != -1) {
                    total += read;
                    if (total > maxBytes) {
                        throw new FileTooLargeException(maxBytes);
                    }
                    sha256.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            return new StagedBlob(temp, total, HexFormat.of().formatHex(sha256.digest()));
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new UncheckedIOException("Could not stage upload", e);
        } catch (NoSuchAlgorithmException e) {
            deleteQuietly(temp);
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        } catch (RuntimeException e) {
            deleteQuietly(temp);
            throw e;
        }
    }

    @Override
    public void promote(StagedBlob staged, UUID storageKey) {
        Path source = requireInside(staged.path(), staging);
        Path target = pathFor(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store blob", e);
        }
    }

    @Override
    public void discard(StagedBlob staged) {
        deleteQuietly(requireInside(staged.path(), staging));
    }

    @Override
    public InputStream open(UUID storageKey) {
        try {
            return Files.newInputStream(pathFor(storageKey), LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not open blob", e);
        }
    }

    @Override
    public Resource resource(UUID storageKey) {
        Path path = pathFor(storageKey);
        if (Files.isSymbolicLink(path)) {
            throw new IllegalStateException("Blob path is a symbolic link");
        }
        return new FileSystemResource(path);
    }

    @Override
    public boolean exists(UUID storageKey) {
        return Files.isRegularFile(pathFor(storageKey), LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public boolean delete(UUID storageKey) {
        try {
            return Files.deleteIfExists(pathFor(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete blob", e);
        }
    }

    @Override
    public List<BlobInfo> listBlobs() {
        List<BlobInfo> result = new ArrayList<>();
        try (Stream<Path> files = Files.walk(blobs, 3)) {
            files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .forEach(
                            p -> {
                                UUID key = parseKey(p.getFileName().toString());
                                if (key != null && pathFor(key).equals(p.normalize())) {
                                    result.add(new BlobInfo(key, lastModified(p)));
                                } else {
                                    log.warn("Ignoring unexpected file in blob storage");
                                }
                            });
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list blobs", e);
        }
        return result;
    }

    @Override
    public int deleteStagedOlderThan(Instant cutoff) {
        int deleted = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(staging)) {
            for (Path file : files) {
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        && lastModified(file).isBefore(cutoff)
                        && Files.deleteIfExists(file)) {
                    deleted++;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not clean staging", e);
        }
        return deleted;
    }

    /** {@code blobs/ab/cd/<uuid>}; the only way a disk path is ever produced. */
    Path pathFor(UUID storageKey) {
        String key = storageKey.toString();
        Path path =
                blobs.resolve(key.substring(0, 2))
                        .resolve(key.substring(2, 4))
                        .resolve(key)
                        .normalize();
        return requireInside(path, blobs);
    }

    private static Path requireInside(Path path, Path directory) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(directory)) {
            throw new IllegalStateException("Resolved path escapes the storage directory");
        }
        return normalized;
    }

    private static UUID parseKey(String name) {
        try {
            UUID key = UUID.fromString(name);
            return key.toString().equals(name) ? key : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read file time", e);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // Left for the reconciler, which removes stale staging files.
            log.warn("Could not remove staging file; the reconciler will retry", e);
        }
    }
}
