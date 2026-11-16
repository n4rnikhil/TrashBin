package app.trashbin.archive;

import static app.trashbin.support.FileFixtures.blobPath;
import static app.trashbin.support.FileFixtures.countFiles;
import static app.trashbin.support.FileFixtures.filled;
import static app.trashbin.support.FileFixtures.pdf;
import static app.trashbin.support.FileFixtures.png;
import static app.trashbin.support.FileFixtures.text;
import static app.trashbin.support.FileFixtures.zip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.trashbin.support.FileFixtures;
import app.trashbin.support.TestData;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/** Upload pipeline and archive reads against the real database and a temp storage root. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FileUploadIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    @Value("${trashbin.storage.root}")
    private Path storageRoot;

    private String alice;

    @BeforeEach
    void setUp() throws Exception {
        TestData.resetDatabase(jdbc);
        FileFixtures.emptyDirectory(storageRoot.resolve("blobs"));
        alice = TestData.bearerFor(mockMvc, json, "alice@example.com");
    }

    // ------------------------------------------------------------------ happy path

    @Test
    void uploadStoresOriginalBytesAndMetadata() throws Exception {
        byte[] content = png("holiday");

        upload(
                        alice,
                        "photo.png",
                        content,
                        "{\"displayName\":\"Holiday\",\"notes\":\"Beach day\","
                                + "\"originDate\":\"2020-04-15\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("Holiday"))
                .andExpect(jsonPath("$.originalFilename").value("photo.png"))
                .andExpect(jsonPath("$.mediaType").value("image/png"))
                .andExpect(jsonPath("$.typeGroup").value("IMAGE"))
                .andExpect(jsonPath("$.sizeBytes").value(content.length))
                .andExpect(jsonPath("$.sha256").value(sha256(content)))
                .andExpect(jsonPath("$.notes").value("Beach day"))
                .andExpect(jsonPath("$.originDate").value("2020-04-15"))
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andExpect(jsonPath("$.ownerId").doesNotExist());

        UUID key = jdbc.queryForObject("select storage_key from archived_files", UUID.class);
        assertThat(Files.readAllBytes(blobPath(storageRoot, key))).isEqualTo(content);
        assertThat(jdbc.queryForObject("select event_type from file_events", String.class))
                .isEqualTo("ARCHIVED");
    }

    @Test
    void pathsInFileNamesAreStrippedAndNeverReachTheDisk() throws Exception {
        upload(alice, "..\\..\\evil.txt", text("not a path"), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("evil.txt"))
                .andExpect(jsonPath("$.mediaType").value("text/plain"))
                .andExpect(jsonPath("$.typeGroup").value("TEXT"));

        UUID key = jdbc.queryForObject("select storage_key from archived_files", UUID.class);
        assertThat(Files.isRegularFile(blobPath(storageRoot, key))).isTrue();
        assertThat(countFiles(storageRoot.resolve("blobs"))).isEqualTo(1);
    }

    @Test
    void officeDocumentsInZipContainersAreAccepted() throws Exception {
        upload(alice, "letter.docx", zip("docx"), null).andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ duplicates

    @Test
    void duplicatesAreReportedUnlessExplicitlyAllowed() throws Exception {
        String firstId =
                id(
                        upload(alice, "a.txt", text("same bytes"), null)
                                .andExpect(status().isCreated()));

        upload(alice, "b.txt", text("same bytes"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_FILE"))
                .andExpect(jsonPath("$.existing.id").value(firstId))
                .andExpect(jsonPath("$.existing.displayName").value("a.txt"));
        assertThat(countFiles(storageRoot.resolve("blobs"))).isEqualTo(1);

        upload(alice, "b.txt", text("same bytes"), "{\"allowDuplicate\":true}")
                .andExpect(status().isCreated());
        assertThat(rowCount()).isEqualTo(2);
    }

    @Test
    void duplicateDetectionIsPerOwner() throws Exception {
        String bob = TestData.bearerFor(mockMvc, json, "bob@example.com");
        upload(alice, "a.txt", text("shared bytes"), null).andExpect(status().isCreated());

        upload(bob, "a.txt", text("shared bytes"), null).andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ rejections

    @Test
    void contentThatContradictsItsExtensionIsRejectedAndNothingIsKept() throws Exception {
        long stagingBefore = countFiles(storageRoot.resolve("staging"));

        upload(alice, "report.pdf", text("I am not a PDF"), null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("FILE_SIGNATURE_MISMATCH"));

        assertThat(rowCount()).isZero();
        assertThat(countFiles(storageRoot.resolve("blobs"))).isZero();
        assertThat(countFiles(storageRoot.resolve("staging"))).isEqualTo(stagingBefore);
    }

    @Test
    void genuinePdfIsAccepted() throws Exception {
        upload(alice, "report.pdf", pdf("real"), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.typeGroup").value("DOCUMENT"));
    }

    @Test
    void blockedExtensionsAreRejectedCaseInsensitively() throws Exception {
        upload(alice, "setup.EXE", text("MZ"), null)
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        upload(alice, "saver.scr", text("MZ"), null).andExpect(status().isUnsupportedMediaType());
        assertThat(rowCount()).isZero();
    }

    @Test
    void filesOverTheSizeLimitAreRejected() throws Exception {
        upload(alice, "big.bin", filled(1024 * 1024 + 1, (byte) 1), null)
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        assertThat(countFiles(storageRoot.resolve("blobs"))).isZero();
    }

    @Test
    void quotaIsEnforcedPerUser() throws Exception {
        for (byte b = 1; b <= 3; b++) {
            upload(alice, "part" + b + ".bin", filled(900 * 1024, b), null)
                    .andExpect(status().isCreated());
        }

        upload(alice, "part4.bin", filled(900 * 1024, (byte) 4), null)
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("QUOTA_EXCEEDED"));
        assertThat(countFiles(storageRoot.resolve("blobs"))).isEqualTo(3);
    }

    @Test
    void invalidMetadataIsRejected() throws Exception {
        upload(alice, "a.txt", text("x"), "{\"notes\":\"" + "n".repeat(10_001) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(rowCount()).isZero();
    }

    @Test
    void uploadRequiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/v1/files").file(filePart("a.txt", text("x"))))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ reads

    @Test
    void listIsOwnerScopedPagedAndSorted() throws Exception {
        for (String name : new String[] {"b.txt", "a.txt", "c.txt"}) {
            upload(alice, name, text("content of " + name), null).andExpect(status().isCreated());
        }
        String bob = TestData.bearerFor(mockMvc, json, "bob@example.com");

        mockMvc.perform(
                        get("/api/v1/files?sort=displayName,asc&size=2")
                                .header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].displayName").value("a.txt"))
                .andExpect(jsonPath("$.content[1].displayName").value("b.txt"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/v1/files").header("Authorization", bob))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listRejectsUnsupportedParameters() throws Exception {
        mockMvc.perform(get("/api/v1/files?sort=sha256,asc").header("Authorization", alice))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/files?size=101").header("Authorization", alice))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/files?status=PURGED").header("Authorization", alice))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detailIsVisibleOnlyToItsOwner() throws Exception {
        String id = id(upload(alice, "a.txt", text("private"), null));
        String bob = TestData.bearerFor(mockMvc, json, "bob@example.com");

        mockMvc.perform(get("/api/v1/files/" + id).header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.id").value(id));
        mockMvc.perform(get("/api/v1/files/" + id).header("Authorization", bob))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/v1/files/" + UUID.randomUUID()).header("Authorization", alice))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/files/not-a-uuid").header("Authorization", alice))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/files/" + id)).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions upload(String bearer, String name, byte[] content, String metadata)
            throws Exception {
        MockMultipartHttpServletRequestBuilder request =
                multipart("/api/v1/files").file(filePart(name, content));
        if (metadata != null) {
            request.file(
                    new MockMultipartFile(
                            "metadata",
                            "",
                            "application/json",
                            metadata.getBytes(StandardCharsets.UTF_8)));
        }
        return mockMvc.perform(request.header("Authorization", bearer));
    }

    private static MockMultipartFile filePart(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    private String id(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString())
                .get("id")
                .asString();
    }

    private long rowCount() {
        Long count = jdbc.queryForObject("select count(*) from archived_files", Long.class);
        return count == null ? 0 : count;
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
