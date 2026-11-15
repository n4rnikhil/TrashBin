package app.trashbin.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.util.FileSystemUtils;

/** Minimal byte fixtures with real file signatures, and storage-directory helpers. */
public final class FileFixtures {

    private FileFixtures() {}

    public static byte[] png(String salt) {
        return withHeader(
                new byte[] {
                    (byte) 0x89,
                    'P',
                    'N',
                    'G',
                    '\r',
                    '\n',
                    0x1a,
                    '\n',
                    0,
                    0,
                    0,
                    13,
                    'I',
                    'H',
                    'D',
                    'R'
                },
                salt);
    }

    public static byte[] pdf(String salt) {
        return withHeader("%PDF-1.4\n%âã\n".getBytes(StandardCharsets.ISO_8859_1), salt);
    }

    public static byte[] zip(String salt) {
        return withHeader(new byte[] {'P', 'K', 3, 4, 20, 0, 0, 0}, salt);
    }

    public static byte[] text(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] filled(int size, byte value) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, value);
        return bytes;
    }

    private static byte[] withHeader(byte[] header, String salt) {
        byte[] tail = ("\n" + salt + "\n").getBytes(StandardCharsets.UTF_8);
        byte[] result = Arrays.copyOf(header, header.length + tail.length);
        System.arraycopy(tail, 0, result, header.length, tail.length);
        return result;
    }

    /** The blob path for a storage key, mirroring LocalFsBlobStore's layout. */
    public static Path blobPath(Path root, UUID key) {
        String k = key.toString();
        return root.resolve("blobs")
                .resolve(k.substring(0, 2))
                .resolve(k.substring(2, 4))
                .resolve(k);
    }

    public static void emptyDirectory(Path dir) {
        try {
            FileSystemUtils.deleteRecursively(dir);
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static long countFiles(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void makeOld(Path file) {
        try {
            Files.setLastModifiedTime(file, FileTime.from(Instant.now().minusSeconds(7200)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
