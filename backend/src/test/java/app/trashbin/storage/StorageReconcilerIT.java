package app.trashbin.storage;

import static app.trashbin.support.FileFixtures.blobPath;
import static app.trashbin.support.FileFixtures.makeOld;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.trashbin.support.FileFixtures;
import app.trashbin.support.TestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StorageReconcilerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    @Value("${trashbin.storage.root}")
    private Path root;

    private String admin;
    private String user;

    @BeforeEach
    void setUp() throws Exception {
        TestData.resetDatabase(jdbc);
        FileFixtures.emptyDirectory(root.resolve("blobs"));
        FileFixtures.emptyDirectory(root.resolve("staging"));
        admin = TestData.bearerFor(mockMvc, json, "admin@example.com");
        user = TestData.bearerFor(mockMvc, json, "user@example.com");
    }

    @Test
    void removesOldOrphansKeepsReferencedAndRecentBlobsAndReportsMissingOnes() throws Exception {
        upload("kept.txt", "referenced");
        upload("lost.txt", "will lose its blob");
        List<UUID> keys =
                jdbc.queryForList(
                        "select storage_key from archived_files order by display_name", UUID.class);
        Path kept = blobPath(root, keys.get(0));
        makeOld(kept);
        Files.delete(blobPath(root, keys.get(1)));

        Path oldOrphan = writeBlob(UUID.randomUUID());
        makeOld(oldOrphan);
        Path recentOrphan = writeBlob(UUID.randomUUID());
        Path oldStaging = Files.writeString(root.resolve("staging/upload-1.part"), "abandoned");
        makeOld(oldStaging);

        mockMvc.perform(post("/api/v1/admin/storage/reconcile").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stagingFilesRemoved").value(1))
                .andExpect(jsonPath("$.orphanBlobsRemoved").value(1))
                .andExpect(jsonPath("$.missingBlobs").value(1));

        assertThat(kept).exists();
        assertThat(recentOrphan).exists();
        assertThat(oldOrphan).doesNotExist();
        assertThat(oldStaging).doesNotExist();
    }

    @Test
    void reconcileIsAdminOnly() throws Exception {
        mockMvc.perform(post("/api/v1/admin/storage/reconcile").header("Authorization", user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(post("/api/v1/admin/storage/reconcile"))
                .andExpect(status().isUnauthorized());
    }

    private void upload(String name, String content) throws Exception {
        mockMvc.perform(
                        multipart("/api/v1/files")
                                .file(
                                        new MockMultipartFile(
                                                "file",
                                                name,
                                                "text/plain",
                                                FileFixtures.text(content)))
                                .header("Authorization", user))
                .andExpect(status().isCreated());
    }

    private Path writeBlob(UUID key) throws Exception {
        Path path = blobPath(root, key);
        Files.createDirectories(path.getParent());
        return Files.writeString(path, "orphan");
    }
}
