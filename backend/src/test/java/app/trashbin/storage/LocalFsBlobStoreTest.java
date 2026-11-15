package app.trashbin.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.trashbin.common.error.FileTooLargeException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFsBlobStoreTest {

    @TempDir Path root;
    private LocalFsBlobStore store;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties(root.toString());
        new StorageRootInitializer(properties).afterPropertiesSet();
        store = new LocalFsBlobStore(properties);
    }

    @Test
    void stagingHashesAndCountsTheBytesActuallyReceived() {
        StagedBlob staged = store.stage(stream("hello"), 100);

        assertThat(staged.sizeBytes()).isEqualTo(5);
        assertThat(staged.sha256())
                .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
        assertThat(staged.path()).startsWith(root.resolve("staging"));
    }

    @Test
    void stagingOverTheLimitFailsAndLeavesNothingBehind() throws Exception {
        assertThatThrownBy(() -> store.stage(stream("12345678901"), 10))
                .isInstanceOf(FileTooLargeException.class);

        try (var files = Files.list(root.resolve("staging"))) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void promoteMovesTheBlobToItsShardedLocation() throws Exception {
        UUID key = UUID.fromString("abcdef01-2345-6789-abcd-ef0123456789");
        StagedBlob staged = store.stage(stream("payload"), 100);

        store.promote(staged, key);

        Path expected = root.resolve("blobs/ab/cd/" + key);
        assertThat(expected).hasContent("payload");
        assertThat(staged.path()).doesNotExist();
        assertThat(store.exists(key)).isTrue();
        try (InputStream in = store.open(key)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("payload");
        }
        assertThat(store.delete(key)).isTrue();
        assertThat(store.delete(key)).isFalse();
    }

    @Test
    void discardIsIdempotent() {
        StagedBlob staged = store.stage(stream("x"), 10);

        store.discard(staged);
        store.discard(staged);

        assertThat(staged.path()).doesNotExist();
    }

    @Test
    void stagedFilesOutsideTheStagingDirectoryAreRefused() {
        StagedBlob forged =
                new StagedBlob(root.resolve("blobs/../../outside.txt"), 1, "0".repeat(64));

        assertThatThrownBy(() -> store.discard(forged)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void listingIgnoresFilesThatAreNotBlobs() throws Exception {
        UUID key = UUID.randomUUID();
        store.promote(store.stage(stream("a"), 10), key);
        Files.writeString(root.resolve("blobs/stray.txt"), "?");

        assertThat(store.listBlobs())
                .extracting(BlobStore.BlobInfo::storageKey)
                .containsExactly(key);
    }

    @Test
    void onlyOldStagingFilesAreCleanedUp() throws Exception {
        Path old = Files.writeString(root.resolve("staging/old.part"), "old");
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minusSeconds(7200)));
        Path recent = Files.writeString(root.resolve("staging/new.part"), "new");

        assertThat(store.deleteStagedOlderThan(Instant.now().minusSeconds(3600))).isEqualTo(1);
        assertThat(old).doesNotExist();
        assertThat(recent).exists();
    }

    private static InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
