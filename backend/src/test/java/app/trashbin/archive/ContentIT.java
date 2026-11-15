package app.trashbin.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.trashbin.support.FileFixtures;
import app.trashbin.support.MutableClock;
import app.trashbin.support.TestClockConfig;
import app.trashbin.support.TestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** Download and preview through signed links (docs/SECURITY_GUIDELINES.md section 7). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class ContentIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MutableClock clock;

    @Value("${trashbin.storage.root}")
    private Path storageRoot;

    private String alice;

    @BeforeEach
    void setUp() throws Exception {
        TestData.resetDatabase(jdbc);
        FileFixtures.emptyDirectory(storageRoot.resolve("blobs"));
        alice = TestData.bearerFor(mockMvc, json, "alice@example.com");
    }

    @Test
    void downloadIsAnAttachmentWithTheOriginalBytesAndSafeHeaders() throws Exception {
        byte[] content = FileFixtures.pdf("download me");
        String id = upload("lease.pdf", content);

        mockMvc.perform(get(url(id, "DOWNLOAD")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(
                        header().string(
                                        "Content-Disposition",
                                        org.hamcrest.Matchers.startsWith("attachment")))
                .andExpect(
                        header().string(
                                        "Content-Disposition",
                                        org.hamcrest.Matchers.containsString("lease.pdf")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(
                        header().string(
                                        "Cache-Control",
                                        org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(
                        header().string(
                                        "Content-Security-Policy",
                                        org.hamcrest.Matchers.containsString("sandbox")))
                .andExpect(
                        result ->
                                assertThat(result.getResponse().getContentAsByteArray())
                                        .isEqualTo(content));
    }

    @Test
    void previewsAreInlineOnlyForAllowlistedTypes() throws Exception {
        String png = upload("photo.png", FileFixtures.png("p"));
        String pdf = upload("doc.pdf", FileFixtures.pdf("d"));
        String text = upload("notes.txt", FileFixtures.text("hello <script>alert(1)</script>"));
        String zip = upload("bundle.zip", FileFixtures.zip("z"));

        mockMvc.perform(get(url(png, "PREVIEW")))
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(
                        header().string(
                                        "Content-Disposition",
                                        org.hamcrest.Matchers.startsWith("inline")));
        mockMvc.perform(get(url(pdf, "PREVIEW")))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Security-Policy", "frame-ancestors 'self'"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
        // Text is always plain text, never HTML, and sandboxed.
        mockMvc.perform(get(url(text, "PREVIEW")))
                .andExpect(header().string("Content-Type", "text/plain;charset=UTF-8"))
                .andExpect(
                        header().string(
                                        "Content-Security-Policy",
                                        org.hamcrest.Matchers.containsString("sandbox")));

        ticket(zip, "PREVIEW")
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mockMvc.perform(get("/api/v1/files/" + zip).header("Authorization", alice))
                .andExpect(jsonPath("$.previewable").value(false));
        mockMvc.perform(get("/api/v1/files/" + png).header("Authorization", alice))
                .andExpect(jsonPath("$.previewable").value(true));
    }

    @Test
    void rangeRequestsAreSupported() throws Exception {
        String id = upload("data.txt", FileFixtures.text("0123456789"));
        String link = url(id, "DOWNLOAD");

        mockMvc.perform(get(link).header("Range", "bytes=2-5"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 2-5/10"))
                .andExpect(
                        result ->
                                assertThat(result.getResponse().getContentAsString())
                                        .isEqualTo("2345"));
        mockMvc.perform(get(link).header("Range", "bytes=50-60"))
                .andExpect(status().isRequestedRangeNotSatisfiable());
    }

    @Test
    void expiredTamperedOrOrphanedLinksAreRefused() throws Exception {
        String id = upload("a.txt", FileFixtures.text("secret"));
        String link = url(id, "DOWNLOAD");

        mockMvc.perform(get(link.substring(0, link.length() - 2) + "xx"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TICKET_INVALID"));
        mockMvc.perform(get("/api/v1/content/garbage")).andExpect(status().isForbidden());

        clock.advance(Duration.ofSeconds(61));
        mockMvc.perform(get(link)).andExpect(status().isForbidden());

        // A fresh link for a file that is then removed must not serve anything.
        String fresh = url(id, "DOWNLOAD");
        jdbc.update("delete from archived_files where id = ?::uuid", id);
        mockMvc.perform(get(fresh)).andExpect(status().isNotFound());
    }

    @Test
    void linksAreOnlyIssuedToTheOwner() throws Exception {
        String id = upload("a.txt", FileFixtures.text("mine"));
        String bob = TestData.bearerFor(mockMvc, json, "bob@example.com");

        mockMvc.perform(
                        post("/api/v1/files/" + id + "/access-tickets")
                                .header("Authorization", bob)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"purpose\":\"DOWNLOAD\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(
                        post("/api/v1/files/" + id + "/access-tickets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"purpose\":\"DOWNLOAD\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessIsRecordedWithoutCausingEditConflicts() throws Exception {
        String id = upload("a.txt", FileFixtures.text("track me"));

        url(id, "DOWNLOAD");
        url(id, "PREVIEW");

        assertThat(
                        jdbc.queryForList(
                                "select event_type from file_events order by occurred_at",
                                String.class))
                .containsExactly("ARCHIVED", "DOWNLOADED", "PREVIEWED");
        assertThat(
                        jdbc.queryForObject(
                                "select last_accessed_at is not null from archived_files",
                                Boolean.class))
                .isTrue();
        // Version 0 is still current: opening the file did not bump it.
        mockMvc.perform(
                        patch("/api/v1/files/" + id)
                                .header("Authorization", alice)
                                .header("If-Match", "\"0\"")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"displayName\":\"Renamed\"}"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ helpers

    private String upload(String name, byte[] content) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                multipart("/api/v1/files")
                                        .file(
                                                new MockMultipartFile(
                                                        "file",
                                                        name,
                                                        "application/octet-stream",
                                                        content))
                                        .header("Authorization", alice))
                        .andExpect(status().isCreated())
                        .andReturn();
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("id")
                .asText();
    }

    private ResultActions ticket(String id, String purpose) throws Exception {
        return mockMvc.perform(
                post("/api/v1/files/" + id + "/access-tickets")
                        .header("Authorization", alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"" + purpose + "\"}"));
    }

    private String url(String id, String purpose) throws Exception {
        String body =
                ticket(id, purpose)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.expiresAt").exists())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body).get("url").asText();
    }
}
