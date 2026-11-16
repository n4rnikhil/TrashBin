package app.trashbin.collection;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.trashbin.support.ApiClient;
import app.trashbin.support.FileFixtures;
import app.trashbin.support.TestData;
import java.nio.file.Path;
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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CollectionFlowIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    @Value("${trashbin.storage.root}")
    private Path storageRoot;

    private ApiClient api;
    private String alice;
    private String bob;

    @BeforeEach
    void setUp() throws Exception {
        TestData.resetDatabase(jdbc);
        FileFixtures.emptyDirectory(storageRoot.resolve("blobs"));
        api = new ApiClient(mockMvc, json);
        alice = TestData.bearerFor(mockMvc, json, "alice@example.com");
        bob = TestData.bearerFor(mockMvc, json, "bob@example.com");
    }

    @Test
    void createRenameDescribeAndRejectDuplicateNames() throws Exception {
        String id = api.createCollection(alice, "  House   purchase ");

        mockMvc.perform(get("/api/v1/collections/" + id).header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("House purchase"))
                .andExpect(jsonPath("$.fileCount").value(0));
        create(alice, "house PURCHASE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COLLECTION_ALREADY_EXISTS"));

        update(alice, id, Map.of("name", "Home", "description", "Deeds and surveys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Home"))
                .andExpect(jsonPath("$.description").value("Deeds and surveys"));
        update(alice, id, Map.of("description", ""))
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void filesJoinCollectionsFromTheFileSideAndCanBeFilteredByThem() throws Exception {
        String taxes = api.createCollection(alice, "Taxes");
        String house = api.createCollection(alice, "House");
        String a = api.upload(alice, "a.txt", "a", Map.of("collectionIds", List.of(taxes)));
        String b = api.upload(alice, "b.txt", "b", null);

        setCollections(alice, b, List.of(taxes, house))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collections.length()").value(2))
                .andExpect(jsonPath("$.collections[0].name").value("House"));

        mockMvc.perform(get("/api/v1/files?collection=" + taxes).header("Authorization", alice))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/files?collection=" + house).header("Authorization", alice))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(b));
        mockMvc.perform(get("/api/v1/collections").header("Authorization", alice))
                .andExpect(jsonPath("$[0].name").value("House"))
                .andExpect(jsonPath("$[0].fileCount").value(1))
                .andExpect(jsonPath("$[1].fileCount").value(2));

        setCollections(alice, a, List.of()).andExpect(jsonPath("$.collections.length()").value(0));
    }

    @Test
    void deletingACollectionKeepsItsFiles() throws Exception {
        String taxes = api.createCollection(alice, "Taxes");
        String file = api.upload(alice, "a.txt", "a", Map.of("collectionIds", List.of(taxes)));

        mockMvc.perform(delete("/api/v1/collections/" + taxes).header("Authorization", alice))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/files/" + file).header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collections.length()").value(0));
    }

    @Test
    void collectionsAreInvisibleAndUnusableAcrossUsers() throws Exception {
        String alicesCollection = api.createCollection(alice, "Private");
        String bobsFile = api.upload(bob, "b.txt", "b", null);
        String alicesFile = api.upload(alice, "a.txt", "a", null);

        mockMvc.perform(get("/api/v1/collections/" + alicesCollection).header("Authorization", bob))
                .andExpect(status().isNotFound());
        update(bob, alicesCollection, Map.of("name", "stolen")).andExpect(status().isNotFound());
        mockMvc.perform(
                        delete("/api/v1/collections/" + alicesCollection)
                                .header("Authorization", bob))
                .andExpect(status().isNotFound());
        // Bob cannot put his file into Alice's collection, nor touch Alice's file.
        setCollections(bob, bobsFile, List.of(alicesCollection)).andExpect(status().isNotFound());
        setCollections(bob, alicesFile, List.of()).andExpect(status().isNotFound());
        // Filtering by a foreign collection simply finds nothing.
        mockMvc.perform(
                        get("/api/v1/files?collection=" + alicesCollection)
                                .header("Authorization", bob))
                .andExpect(jsonPath("$.totalElements").value(0));
        setCollections(alice, alicesFile, List.of(UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    private ResultActions create(String bearer, String name) throws Exception {
        return mockMvc.perform(
                post("/api/v1/collections")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", name))));
    }

    private ResultActions update(String bearer, String id, Map<String, String> body)
            throws Exception {
        return mockMvc.perform(
                patch("/api/v1/collections/" + id)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)));
    }

    private ResultActions setCollections(String bearer, String fileId, List<String> ids)
            throws Exception {
        return mockMvc.perform(
                put("/api/v1/files/" + fileId + "/collections")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("collectionIds", ids))));
    }
}
