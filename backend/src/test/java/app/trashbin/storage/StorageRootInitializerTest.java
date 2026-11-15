package app.trashbin.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageRootInitializerTest {

    @TempDir Path tempDir;

    @Test
    void createsStagingAndBlobsDirectories() {
        Path root = tempDir.resolve("data");

        new StorageRootInitializer(new StorageProperties(root.toString())).afterPropertiesSet();

        assertThat(root.resolve("staging")).isDirectory();
        assertThat(root.resolve("blobs")).isDirectory();
    }

    @Test
    void isIdempotent() {
        StorageRootInitializer initializer =
                new StorageRootInitializer(
                        new StorageProperties(tempDir.resolve("data").toString()));

        initializer.afterPropertiesSet();
        initializer.afterPropertiesSet();

        assertThat(tempDir.resolve("data/blobs")).isDirectory();
    }

    @Test
    void rejectsRelativePath() {
        StorageRootInitializer initializer =
                new StorageRootInitializer(new StorageProperties("relative/data"));

        assertThatThrownBy(initializer::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("absolute");
    }

    @Test
    void rejectsRootInsideGitWorkingTree() throws Exception {
        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo.resolve(".git"));
        StorageRootInitializer initializer =
                new StorageRootInitializer(new StorageProperties(repo.resolve("data").toString()));

        assertThatThrownBy(initializer::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outside any git working tree");
    }
}
