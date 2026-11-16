package app.trashbin.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.trashbin.support.ApiClient;
import app.trashbin.support.FileFixtures;
import app.trashbin.support.TestData;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Full-text search, fuzzy file-name matching, filters and index upkeep. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FileSearchIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    @Value("${trashbin.storage.root}")
    private Path storageRoot;

    private ApiClient api;
    private String alice;
    private String lease;
    private String photo;
    private String budget;

    @BeforeEach
    void setUp() throws Exception {
        TestData.resetDatabase(jdbc);
        FileFixtures.emptyDirectory(storageRoot.resolve("blobs"));
        api = new ApiClient(mockMvc, json);
        alice = TestData.bearerFor(mockMvc, json, "alice@example.com");
        lease =
                api.upload(
                        alice,
                        "Apartment_lease-2021.txt",
                        "lease",
                        Map.of(
                                "notes",
                                "Signed with the landlord in March",
                                "tags",
                                List.of("housing", "contracts")));
        photo =
                api.upload(
                        alice,
                        "beach.txt",
                        "photo",
                        Map.of(
                                "contextNote",
                                "First trip after the move",
                                "tags",
                                List.of("travel")));
        budget =
                api.upload(
                        alice,
                        "budget.txt",
                        "budget",
                        Map.of(
                                "notes",
                                "Includes the apartment deposit",
                                "tags",
                                List.of("housing")));
    }

    @Test
    void searchesNamesNotesContextAndTagsWithStemming() throws Exception {
        assertThat(ids("q=leases")).containsExactly(lease); // stemming + split file name
        assertThat(ids("q=landlord")).containsExactly(lease); // notes
        assertThat(ids("q=trip")).containsExactly(photo); // context note
        assertThat(ids("q=travel")).containsExactly(photo); // tag
        assertThat(ids("q=nothing-like-this")).isEmpty();
    }

    @Test
    void ranksNameMatchesAboveNoteMatches() throws Exception {
        assertThat(ids("q=apartment")).containsExactly(lease, budget);
    }

    @Test
    void matchesPartialAndMisspelledFileNames() throws Exception {
        assertThat(ids("q=partme")).containsExactly(lease); // substring
        assertThat(ids("q=aprtment")).contains(lease); // typo
    }

    @Test
    void tagFiltersMustAllMatch() throws Exception {
        String housing = tagId("housing");
        String contracts = tagId("contracts");

        assertThat(ids("tag=" + housing)).containsExactlyInAnyOrder(lease, budget);
        assertThat(ids("tag=" + housing + "&tag=" + contracts)).containsExactly(lease);
        assertThat(ids("q=apartment&tag=" + contracts)).containsExactly(lease);
    }

    @Test
    void filtersByTypeFavoriteAndDate() throws Exception {
        api.putJson(alice, "/api/v1/files/" + photo + "/favorite", Map.of("favorite", true));
        jdbc.update(
                "update archived_files set archived_at = '2020-06-15T12:00:00Z' where id = ?::uuid",
                budget);

        assertThat(ids("typeGroup=TEXT")).hasSize(3);
        assertThat(ids("typeGroup=IMAGE")).isEmpty();
        assertThat(ids("favorite=true")).containsExactly(photo);
        assertThat(ids("from=2020-06-15&to=2020-06-15")).containsExactly(budget);
        assertThat(ids("to=2020-12-31")).containsExactly(budget);
        assertThat(ids("from=2021-01-01")).containsExactlyInAnyOrder(lease, photo);
    }

    @Test
    void renamingOrDeletingATagUpdatesSearch() throws Exception {
        String travel = tagId("travel");
        mockMvc.perform(
                        patch("/api/v1/tags/" + travel)
                                .header("Authorization", alice)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"holidays\"}"))
                .andExpect(status().isOk());

        assertThat(ids("q=holidays")).containsExactly(photo);
        assertThat(ids("q=travel")).isEmpty();
    }

    @Test
    void editingMetadataUpdatesSearch() throws Exception {
        api.putJson(alice, "/api/v1/files/" + budget + "/tags", Map.of("tags", List.of("finance")));
        assertThat(ids("q=finance")).containsExactly(budget);

        mockMvc.perform(
                        patch("/api/v1/files/" + photo)
                                .header("Authorization", alice)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"notes\":\"Portugal coastline\"}"))
                .andExpect(status().isOk());
        assertThat(ids("q=portugal")).containsExactly(photo);
    }

    @Test
    void neverReturnsAnotherUsersFiles() throws Exception {
        String bob = TestData.bearerFor(mockMvc, json, "bob@example.com");

        mockMvc.perform(get("/api/v1/files?q=apartment").header("Authorization", bob))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/files?tag=" + tagId("housing")).header("Authorization", bob))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void searchTextIsNeverInterpretedAsSqlOrPattern() throws Exception {
        assertThat(ids("q=" + enc("'; drop table archived_files; --"))).isEmpty();
        assertThat(ids("q=" + enc("%"))).isEmpty();
        // "_" is matched literally (only the name containing it), never as a LIKE wildcard.
        assertThat(ids("q=" + enc("_"))).containsExactly(lease);
        assertThat(ids("q=" + enc("\"lease\" OR -beach"))).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from archived_files", Long.class))
                .isEqualTo(3);
    }

    @Test
    void rejectsInvalidSearchParameters() throws Exception {
        expectBadRequest("sort=relevance");
        expectBadRequest("q=" + "x".repeat(201));
        expectBadRequest("from=2021-01-02&to=2021-01-01");
        expectBadRequest("typeGroup=SPACESHIP");
        expectBadRequest("from=yesterday");
        mockMvc.perform(
                        get("/api/v1/files?q=apartment&sort=displayName,asc")
                                .header("Authorization", alice))
                .andExpect(jsonPath("$.content[0].displayName").value("Apartment_lease-2021.txt"));
    }

    // ------------------------------------------------------------------ helpers

    private List<String> ids(String query) throws Exception {
        JsonNode page =
                api.read(
                        mockMvc.perform(
                                        get("/api/v1/files?" + query)
                                                .header("Authorization", alice))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        List<String> ids = new ArrayList<>();
        page.get("content").forEach(f -> ids.add(f.get("id").asString()));
        return ids;
    }

    private void expectBadRequest(String query) throws Exception {
        mockMvc.perform(get("/api/v1/files?" + query).header("Authorization", alice))
                .andExpect(status().isBadRequest());
    }

    private String tagId(String name) {
        return jdbc.queryForObject(
                        "select id from tags where name_normalized = ?", UUID.class, name)
                .toString();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
