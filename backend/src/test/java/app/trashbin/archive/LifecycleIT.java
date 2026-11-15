package app.trashbin.archive;

import static app.trashbin.support.FileFixtures.blobPath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.trashbin.archive.service.RetentionJob;
import app.trashbin.storage.StorageReconciler;
import app.trashbin.support.ApiClient;
import app.trashbin.support.FileFixtures;
import app.trashbin.support.MutableClock;
import app.trashbin.support.TestClockConfig;
import app.trashbin.support.TestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Discard → restore → purge, retention, integrity, timeline and statistics (M5). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class LifecycleIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MutableClock clock;
    @Autowired private RetentionJob retention;
    @Autowired private StorageReconciler reconciler;

    @Value("${trashbin.storage.root}")
    private Path storageRoot;

    private ApiClient api;
    private String alice;

    @BeforeEach
    void setUp() throws Exception {
        TestData.resetDatabase(jdbc);
        FileFixtures.emptyDirectory(storageRoot.resolve("blobs"));
        clock.set(java.time.Instant.now());
        api = new ApiClient(mockMvc, json);
        alice = TestData.bearerFor(mockMvc, json, "alice@example.com");
    }

    @Test
    void discardRestoreAndInvalidTransitions() throws Exception {
        String id = api.upload(alice, "a.txt", "keep me", Map.of("tags", List.of("t")));

        action(id, "discard")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCARDED"))
                .andExpect(jsonPath("$.discardedAt").exists())
                .andExpect(jsonPath("$.purgeAfter").exists());
        action(id, "discard")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        // Discarded files leave the archive view but stay listed under Discarded.
        mockMvc.perform(get("/api/v1/files").header("Authorization", alice))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/files?status=DISCARDED").header("Authorization", alice))
                .andExpect(jsonPath("$.content[0].id").value(id));

        action(id, "restore")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.discardedAt").doesNotExist())
                .andExpect(jsonPath("$.purgeAfter").doesNotExist())
                .andExpect(jsonPath("$.tags[0].name").value("t"));
        action(id, "restore").andExpect(status().isConflict());
    }

    @Test
    void onlyDiscardedFilesCanBePurgedAndPurgeRemovesEverything() throws Exception {
        String id = api.upload(alice, "a.txt", "goodbye", Map.of("tags", List.of("t")));
        UUID key = storageKey(id);

        mockMvc.perform(delete("/api/v1/files/" + id).header("Authorization", alice))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        action(id, "discard");
        mockMvc.perform(delete("/api/v1/files/" + id).header("Authorization", alice))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/files/" + id).header("Authorization", alice))
                .andExpect(status().isNotFound());
        assertThat(count("archived_files")).isZero();
        assertThat(count("file_tags")).isZero();
        assertThat(count("file_events")).isZero();
        assertThat(blobPath(storageRoot, key)).doesNotExist();
        // The tag itself survives; only the link went away.
        assertThat(count("tags")).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("try") // the open stream is the point: it holds a lock on the blob
    void blobsThatCannotBeDeletedRightAwayAreCleanedUpByTheReconciler() throws Exception {
        String id = api.upload(alice, "a.txt", "locked", null);
        Path blob = blobPath(storageRoot, storageKey(id));
        action(id, "discard");

        // On Windows an open handle blocks deletion; elsewhere the delete simply succeeds.
        try (InputStream ignored = Files.newInputStream(blob)) {
            mockMvc.perform(delete("/api/v1/files/" + id).header("Authorization", alice))
                    .andExpect(status().isNoContent());
        }
        assertThat(count("archived_files")).isZero();

        if (Files.exists(blob)) {
            FileFixtures.makeOld(blob);
            reconciler.reconcile();
        }
        assertThat(blob).doesNotExist();
    }

    @Test
    void retentionPurgesOnlyFilesWhoseGracePeriodEnded() throws Exception {
        String old = api.upload(alice, "old.txt", "old", null);
        String recent = api.upload(alice, "recent.txt", "recent", null);
        String kept = api.upload(alice, "kept.txt", "kept", null);
        action(old, "discard");
        clock.advance(Duration.ofDays(20));
        alice = TestData.login(mockMvc, json, "alice@example.com"); // tokens expired meanwhile
        action(recent, "discard");

        clock.advance(Duration.ofDays(11)); // old: 31 days, recent: 11 days
        assertThat(retention.run()).isEqualTo(1);

        assertThat(ids()).containsExactlyInAnyOrder(recent, kept);
        assertThat(retention.run()).isZero();
    }

    @Test
    void integrityCheckDetectsCorruptedBytes() throws Exception {
        String id = api.upload(alice, "a.txt", "pristine", null);

        action(id, "verify").andExpect(jsonPath("$.ok").value(true));

        Files.writeString(blobPath(storageRoot, storageKey(id)), "tampered");
        action(id, "verify")
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.actualSha256").exists());
    }

    @Test
    void timelineListsActivityNewestFirst() throws Exception {
        String id = api.upload(alice, "a.txt", "story", null);
        clock.advance(Duration.ofMinutes(1));
        action(id, "discard");
        clock.advance(Duration.ofMinutes(1));
        action(id, "restore");

        mockMvc.perform(get("/api/v1/files/" + id + "/events").header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].type").value("RESTORED"))
                .andExpect(jsonPath("$.content[1].type").value("DISCARDED"))
                .andExpect(jsonPath("$.content[2].type").value("ARCHIVED"));
    }

    @Test
    void statisticsCountArchivedDiscardedAndStorage() throws Exception {
        api.upload(alice, "a.txt", "12345", null);
        String b = api.upload(alice, "b.txt", "123", null);
        action(b, "discard");

        mockMvc.perform(get("/api/v1/stats").header("Authorization", alice))
                .andExpect(jsonPath("$.archivedCount").value(1))
                .andExpect(jsonPath("$.discardedCount").value(1))
                .andExpect(jsonPath("$.usedBytes").value(8))
                .andExpect(jsonPath("$.quotaBytes").value(3 * 1024 * 1024))
                .andExpect(jsonPath("$.retentionDays").value(30));
    }

    @Test
    void anotherUserCannotTouchTheLifecycle() throws Exception {
        String id = api.upload(alice, "a.txt", "mine", null);
        String bob = TestData.bearerFor(mockMvc, json, "bob@example.com");

        for (String step : List.of("discard", "restore", "verify")) {
            mockMvc.perform(post("/api/v1/files/" + id + "/" + step).header("Authorization", bob))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(delete("/api/v1/files/" + id).header("Authorization", bob))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/files/" + id + "/events").header("Authorization", bob))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/stats").header("Authorization", bob))
                .andExpect(jsonPath("$.archivedCount").value(0));
        assertThat(ids()).containsExactly(id);
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions action(String id, String step) throws Exception {
        return mockMvc.perform(
                post("/api/v1/files/" + id + "/" + step).header("Authorization", alice));
    }

    private UUID storageKey(String id) {
        return jdbc.queryForObject(
                "select storage_key from archived_files where id = ?::uuid", UUID.class, id);
    }

    private long count(String table) {
        Long n = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return n == null ? 0 : n;
    }

    private List<String> ids() {
        return jdbc.queryForList("select id::text from archived_files", String.class);
    }
}
