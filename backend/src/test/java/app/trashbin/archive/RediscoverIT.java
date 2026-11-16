package app.trashbin.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.trashbin.support.ApiClient;
import app.trashbin.support.FileFixtures;
import app.trashbin.support.MutableClock;
import app.trashbin.support.TestClockConfig;
import app.trashbin.support.TestData;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** "On this day", "forgotten" and "surprise me" (FR-M1 – FR-M3). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class RediscoverIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MutableClock clock;

    @Value("${trashbin.storage.root}")
    private Path storageRoot;

    private ApiClient api;
    private String alice;

    @BeforeEach
    void setUp() throws Exception {
        TestData.resetDatabase(jdbc);
        FileFixtures.emptyDirectory(storageRoot.resolve("blobs"));
        clock.set(Instant.parse("2026-06-15T09:00:00Z"));
        api = new ApiClient(mockMvc, json);
        alice = TestData.bearerFor(mockMvc, json, "alice@example.com");
    }

    @Test
    void onThisDayMatchesArchiveOrOriginDateInEarlierYears() throws Exception {
        String archived = file("archived.txt", "2024-06-15T20:00:00Z", null);
        String origin = file("origin.txt", "2026-01-01T00:00:00Z", "2019-06-15");
        file("today.txt", "2026-06-15T08:00:00Z", null); // this year: not a memory yet
        file("other-day.txt", "2024-06-16T10:00:00Z", null);
        String discarded = file("discarded.txt", "2023-06-15T10:00:00Z", null);
        jdbc.update(
                "update archived_files set status = 'DISCARDED', discarded_at = now()"
                        + " where id = ?::uuid",
                discarded);

        assertThat(rediscover("on-this-day")).containsExactlyInAnyOrder(archived, origin);
    }

    @Test
    void leapDayFilesResurfaceOnTheTwentyEighthInOtherYears() throws Exception {
        String leap = file("leap.txt", "2024-02-29T12:00:00Z", null);
        clock.set(Instant.parse("2027-02-28T09:00:00Z"));
        alice = TestData.login(mockMvc, json, "alice@example.com"); // tokens expired meanwhile

        assertThat(rediscover("on-this-day")).containsExactly(leap);
    }

    @Test
    void forgottenMeansOldAndNotOpenedRecently() throws Exception {
        String untouched = file("untouched.txt", "2025-01-01T00:00:00Z", null);
        String opened = file("opened.txt", "2025-01-01T00:00:00Z", null);
        jdbc.update(
                "update archived_files set last_accessed_at = '2026-06-01T00:00:00Z'"
                        + " where id = ?::uuid",
                opened);
        file("new.txt", "2026-06-01T00:00:00Z", null);

        assertThat(rediscover("forgotten")).containsExactly(untouched);
    }

    @Test
    void surpriseMeReturnsOneOfYourOwnFiles() throws Exception {
        String mine = file("mine.txt", "2025-01-01T00:00:00Z", null);
        String bob = TestData.bearerFor(mockMvc, json, "bob@example.com");
        api.upload(bob, "bobs.txt", "bob", null);

        assertThat(rediscover("random")).containsExactly(mine);
        mockMvc.perform(get("/api/v1/rediscover?mode=bogus").header("Authorization", alice))
                .andExpect(status().isBadRequest());
    }

    private String file(String name, String archivedAt, String originDate) throws Exception {
        String id = api.upload(alice, name, name, null);
        jdbc.update(
                "update archived_files set archived_at = ?::timestamptz, origin_date = ?::date"
                        + " where id = ?::uuid",
                archivedAt,
                originDate,
                id);
        return id;
    }

    private List<String> rediscover(String mode) throws Exception {
        JsonNode list =
                api.read(
                        mockMvc.perform(
                                        get("/api/v1/rediscover?mode=" + mode)
                                                .header("Authorization", alice))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        List<String> ids = new ArrayList<>();
        list.forEach(f -> ids.add(f.get("id").asString()));
        return ids;
    }
}
