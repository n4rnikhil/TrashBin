package app.trashbin.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.trashbin.support.FileFixtures;
import app.trashbin.support.TestData;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/** Metadata edits with optimistic concurrency, tags at upload, and favorites. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FileEditIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    @Value("${trashbin.storage.root}")
    private Path storageRoot;

    private String alice;
    private String fileId;

    @BeforeEach
    void setUp() throws Exception {
        TestData.resetDatabase(jdbc);
        FileFixtures.emptyDirectory(storageRoot.resolve("blobs"));
        alice = TestData.bearerFor(mockMvc, json, "alice@example.com");
        fileId =
                json.readTree(
                                mockMvc.perform(
                                                multipart("/api/v1/files")
                                                        .file(
                                                                new MockMultipartFile(
                                                                        "file",
                                                                        "lease.txt",
                                                                        "text/plain",
                                                                        FileFixtures.text("lease")))
                                                        .file(
                                                                new MockMultipartFile(
                                                                        "metadata",
                                                                        "",
                                                                        "application/json",
                                                                        "{\"notes\":\"n\",\"tags\":[\"Tax\",\"2019\",\"tax\"]}"
                                                                                .getBytes(
                                                                                        StandardCharsets
                                                                                                .UTF_8)))
                                                        .header("Authorization", alice))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.tags.length()").value(2))
                                        .andExpect(jsonPath("$.tags[0].name").value("2019"))
                                        .andExpect(jsonPath("$.tags[1].name").value("Tax"))
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .get("id")
                        .asString();
    }

    @Test
    void editsRequireTheCurrentVersion() throws Exception {
        edit(null, Map.of("displayName", "Lease"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"));

        edit("\"0\"", Map.of("displayName", "Lease"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.displayName").value("Lease"))
                .andExpect(jsonPath("$.version").value(1));

        edit("\"0\"", Map.of("displayName", "Stale edit"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        edit("not-an-etag", Map.of("displayName", "x")).andExpect(status().isBadRequest());

        assertThat(
                        jdbc.queryForObject(
                                "select details->>'fields' from file_events where event_type = 'METADATA_EDITED'",
                                String.class))
                .contains("displayName");
    }

    @Test
    void emptyStringsClearOptionalFieldsAndNullsLeaveThemAlone() throws Exception {
        edit("\"0\"", Map.of("contextNote", "Keep for the deposit", "originDate", "2021-03-01"))
                .andExpect(jsonPath("$.notes").value("n"))
                .andExpect(jsonPath("$.contextNote").value("Keep for the deposit"))
                .andExpect(jsonPath("$.originDate").value("2021-03-01"));

        edit("\"1\"", Map.of("notes", "", "originDate", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").doesNotExist())
                .andExpect(jsonPath("$.originDate").doesNotExist())
                .andExpect(jsonPath("$.contextNote").value("Keep for the deposit"));
    }

    @Test
    void invalidEditsAreRejected() throws Exception {
        edit("\"0\"", Map.of("displayName", "   ")).andExpect(status().isBadRequest());
        edit("\"0\"", Map.of("originDate", "2020-13-45")).andExpect(status().isBadRequest());
        edit("\"0\"", Map.of("originDate", "yesterday")).andExpect(status().isBadRequest());
    }

    @Test
    void unchangedEditsDoNotBumpTheVersion() throws Exception {
        edit("\"0\"", Map.of("notes", "n"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void replacingTagsBumpsTheVersion() throws Exception {
        mockMvc.perform(
                        put("/api/v1/files/" + fileId + "/tags")
                                .header("Authorization", alice)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("tags", List.of("home")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[0].name").value("home"))
                .andExpect(jsonPath("$.version").value(1));
        // A stale editor now gets a conflict instead of silently overwriting.
        edit("\"0\"", Map.of("displayName", "x")).andExpect(status().isPreconditionFailed());
    }

    @Test
    void favoritesCanBeToggledAndFiltered() throws Exception {
        mockMvc.perform(
                        multipart("/api/v1/files")
                                .file(
                                        new MockMultipartFile(
                                                "file",
                                                "other.txt",
                                                "text/plain",
                                                FileFixtures.text("other")))
                                .header("Authorization", alice))
                .andExpect(status().isCreated());

        favorite(alice, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorite").value(true));

        mockMvc.perform(get("/api/v1/files?favorite=true").header("Authorization", alice))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(fileId))
                .andExpect(jsonPath("$.content[0].tags.length()").value(2));
        mockMvc.perform(get("/api/v1/files?favorite=false").header("Authorization", alice))
                .andExpect(jsonPath("$.totalElements").value(1));

        favorite(alice, false).andExpect(jsonPath("$.favorite").value(false));
    }

    @Test
    void anotherUserCannotEditTagOrFavoriteTheFile() throws Exception {
        String bob = TestData.bearerFor(mockMvc, json, "bob@example.com");

        mockMvc.perform(
                        patch("/api/v1/files/" + fileId)
                                .header("Authorization", bob)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"displayName\":\"mine now\"}"))
                .andExpect(status().isNotFound());
        favorite(bob, true).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/files/" + fileId).header("Authorization", alice))
                .andExpect(jsonPath("$.displayName").value("lease.txt"))
                .andExpect(jsonPath("$.favorite").value(false));
    }

    private ResultActions edit(String ifMatch, Map<String, String> body) throws Exception {
        var request =
                patch("/api/v1/files/" + fileId)
                        .header("Authorization", alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body));
        if (ifMatch != null) {
            request.header("If-Match", ifMatch);
        }
        return mockMvc.perform(request);
    }

    private ResultActions favorite(String bearer, boolean value) throws Exception {
        return mockMvc.perform(
                put("/api/v1/files/" + fileId + "/favorite")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorite\":" + value + "}"));
    }
}
