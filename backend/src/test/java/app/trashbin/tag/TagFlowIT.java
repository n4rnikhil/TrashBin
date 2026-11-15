package app.trashbin.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.trashbin.support.FileFixtures;
import app.trashbin.support.TestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TagFlowIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    @Value("${trashbin.storage.root}")
    private Path storageRoot;

    private String alice;
    private String bob;

    @BeforeEach
    void setUp() throws Exception {
        TestData.resetDatabase(jdbc);
        FileFixtures.emptyDirectory(storageRoot.resolve("blobs"));
        alice = TestData.bearerFor(mockMvc, json, "alice@example.com");
        bob = TestData.bearerFor(mockMvc, json, "bob@example.com");
    }

    @Test
    void createNormalizesNameAndColorAndRejectsCaseInsensitiveDuplicates() throws Exception {
        create(alice, "  Tax   Returns ", "#3366cc")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Tax Returns"))
                .andExpect(jsonPath("$.color").value("#3366CC"))
                .andExpect(jsonPath("$.fileCount").value(0));

        create(alice, "tax returns", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TAG_ALREADY_EXISTS"));
        // Names are per user.
        create(bob, "Tax Returns", null).andExpect(status().isCreated());
    }

    @Test
    void invalidTagsAreRejected() throws Exception {
        create(alice, "ok", "blue").andExpect(status().isBadRequest());
        create(alice, "   ", null).andExpect(status().isBadRequest());
        create(alice, "x".repeat(51), null).andExpect(status().isBadRequest());
    }

    @Test
    void listShowsOnlyOwnTagsWithFileCounts() throws Exception {
        String fileId = upload(alice, "a.txt", List.of("work", "2019"));
        upload(alice, "b.txt", List.of("work"));

        mockMvc.perform(get("/api/v1/tags").header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("2019"))
                .andExpect(jsonPath("$[0].fileCount").value(1))
                .andExpect(jsonPath("$[1].name").value("work"))
                .andExpect(jsonPath("$[1].fileCount").value(2));
        mockMvc.perform(get("/api/v1/tags").header("Authorization", bob))
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(fileId).isNotBlank();
    }

    @Test
    void renameRecolorAndClearColor() throws Exception {
        String id = id(create(alice, "travel", "#112233"));
        create(alice, "work", null);

        update(alice, id, Map.of("name", "Trips", "color", "#AABBCC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Trips"))
                .andExpect(jsonPath("$.color").value("#AABBCC"));
        update(alice, id, Map.of("color", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.color").doesNotExist());
        update(alice, id, Map.of("name", "WORK"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TAG_ALREADY_EXISTS"));
    }

    @Test
    void mergeMovesFilesAndDeletesTheSource() throws Exception {
        String onlyOld = upload(alice, "a.txt", List.of("old"));
        upload(alice, "b.txt", List.of("old", "new"));
        UUID oldId = tagId("old");
        UUID newId = tagId("new");

        mockMvc.perform(
                        post("/api/v1/tags/" + oldId + "/merge")
                                .header("Authorization", alice)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("targetTagId", newId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("new"))
                .andExpect(jsonPath("$.fileCount").value(2));

        mockMvc.perform(get("/api/v1/files/" + onlyOld).header("Authorization", alice))
                .andExpect(jsonPath("$.tags[0].name").value("new"))
                .andExpect(jsonPath("$.tags.length()").value(1));
        update(alice, oldId.toString(), Map.of("name", "gone")).andExpect(status().isNotFound());
    }

    @Test
    void mergeIntoItselfOrAnotherUsersTagIsRejected() throws Exception {
        String mine = id(create(alice, "mine", null));
        String theirs = id(create(bob, "theirs", null));

        merge(alice, mine, mine).andExpect(status().isBadRequest());
        merge(alice, mine, theirs).andExpect(status().isNotFound());
    }

    @Test
    void deletingATagDetachesItFromFiles() throws Exception {
        String fileId = upload(alice, "a.txt", List.of("temp"));

        mockMvc.perform(delete("/api/v1/tags/" + tagId("temp")).header("Authorization", alice))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/files/" + fileId).header("Authorization", alice))
                .andExpect(jsonPath("$.tags.length()").value(0));
    }

    @Test
    void anotherUsersTagsAreInvisible() throws Exception {
        String id = id(create(alice, "private", null));

        update(bob, id, Map.of("name", "stolen"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(delete("/api/v1/tags/" + id).header("Authorization", bob))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/tags")).andExpect(status().isUnauthorized());
    }

    @Test
    void theDatabaseRefusesToLinkAFileToAnotherUsersTag() throws Exception {
        String aliceFile = upload(alice, "a.txt", List.of());
        String bobTag = id(create(bob, "bobs", null));
        UUID aliceId =
                jdbc.queryForObject(
                        "select owner_id from archived_files where id = ?::uuid",
                        UUID.class,
                        aliceFile);

        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "insert into file_tags (file_id, tag_id, owner_id, created_at)"
                                                + " values (?::uuid, ?::uuid, ?, now())",
                                        aliceFile,
                                        bobTag,
                                        aliceId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void replacingFileTagsCreatesUnknownTagsAndEnforcesTheLimit() throws Exception {
        String fileId = upload(alice, "a.txt", List.of());

        mockMvc.perform(
                        put("/api/v1/files/" + fileId + "/tags")
                                .header("Authorization", alice)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("tags", List.of("B", "a", "b")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags.length()").value(2))
                .andExpect(jsonPath("$.tags[0].name").value("a"))
                .andExpect(jsonPath("$.tags[1].name").value("B"));

        List<String> tooMany =
                java.util.stream.IntStream.range(0, 21).mapToObj(i -> "t" + i).toList();
        mockMvc.perform(
                        put("/api/v1/files/" + fileId + "/tags")
                                .header("Authorization", alice)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("tags", tooMany))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        put("/api/v1/files/" + fileId + "/tags")
                                .header("Authorization", bob)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("tags", List.of("x")))))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions create(String bearer, String name, String color) throws Exception {
        Map<String, String> body =
                color == null ? Map.of("name", name) : Map.of("name", name, "color", color);
        return mockMvc.perform(
                post("/api/v1/tags")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)));
    }

    private ResultActions update(String bearer, String id, Map<String, String> body)
            throws Exception {
        return mockMvc.perform(
                patch("/api/v1/tags/" + id)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)));
    }

    private ResultActions merge(String bearer, String source, String target) throws Exception {
        return mockMvc.perform(
                post("/api/v1/tags/" + source + "/merge")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("targetTagId", target))));
    }

    private String upload(String bearer, String name, List<String> tags) throws Exception {
        String metadata = json.writeValueAsString(Map.of("tags", tags));
        return id(
                mockMvc.perform(
                                multipart("/api/v1/files")
                                        .file(
                                                new MockMultipartFile(
                                                        "file",
                                                        name,
                                                        "text/plain",
                                                        FileFixtures.text(name)))
                                        .file(
                                                new MockMultipartFile(
                                                        "metadata",
                                                        "",
                                                        "application/json",
                                                        metadata.getBytes()))
                                        .header("Authorization", bearer))
                        .andExpect(status().isCreated()));
    }

    private UUID tagId(String name) {
        return jdbc.queryForObject(
                "select id from tags where name_normalized = ?", UUID.class, name.toLowerCase());
    }

    private String id(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString())
                .get("id")
                .asText();
    }
}
