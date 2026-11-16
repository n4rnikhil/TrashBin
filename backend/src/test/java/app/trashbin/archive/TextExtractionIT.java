package app.trashbin.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.trashbin.archive.service.TextExtractionService;
import app.trashbin.support.FileFixtures;
import app.trashbin.support.TestData;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Text extraction (FR-F5) and searching inside file contents. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TextExtractionIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TextExtractionService extraction;

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
    void pdfWordAndTextContentsBecomeSearchable() throws Exception {
        String pdf = upload("scan.pdf", pdf("Mortgage approval from Riverside Bank"));
        String docx = upload("letter.docx", docx("Dear landlord, the boiler is broken"));
        String txt = upload("notes.txt", FileFixtures.text("Remember the quokka photos"));

        assertThat(extractionStatus(pdf)).isEqualTo("PENDING");
        extraction.processPending();

        assertThat(extractionStatus(pdf)).isEqualTo("DONE");
        assertThat(extractionStatus(docx)).isEqualTo("DONE");
        assertThat(extractionStatus(txt)).isEqualTo("DONE");
        assertThat(search("riverside")).containsExactly(pdf);
        assertThat(search("boiler")).containsExactly(docx);
        assertThat(search("quokka")).containsExactly(txt);
    }

    @Test
    void contentSearchNeverCrossesUsers() throws Exception {
        upload("secret.txt", FileFixtures.text("zanzibar treasure map"));
        extraction.processPending();
        String bob = TestData.bearerFor(mockMvc, json, "bob@example.com");

        mockMvc.perform(get("/api/v1/files?q=zanzibar").header("Authorization", bob))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void unsupportedOversizedAndBrokenFilesAreHandledSafely() throws Exception {
        String zip = upload("bundle.zip", FileFixtures.zip("z"));
        String big = upload("big.txt", FileFixtures.filled(600 * 1024, (byte) 'a')); // > 512KB
        String broken = upload("broken.pdf", FileFixtures.pdf("not really a pdf body"));

        extraction.processPending();

        assertThat(extractionStatus(zip)).isEqualTo("SKIPPED");
        assertThat(extractionStatus(big)).isEqualTo("SKIPPED");
        assertThat(extractionStatus(broken)).isEqualTo("FAILED");
        // The application keeps working after a parser failure.
        mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    }

    @Test
    void longTextIsCappedAndMarkedTruncated() throws Exception {
        String id = upload("long.txt", FileFixtures.text("word ".repeat(2_000))); // 10,000 chars

        extraction.processPending();

        assertThat(extractionStatus(id)).isEqualTo("DONE");
        assertThat(jdbc.queryForObject("select truncated from file_text_content", Boolean.class))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "select char_length(content) from file_text_content",
                                Integer.class))
                .isLessThanOrEqualTo(2000);
    }

    @Test
    void extractionNeverCausesEditConflicts() throws Exception {
        String id = upload("a.txt", FileFixtures.text("hello"));

        extraction.processPending();

        mockMvc.perform(get("/api/v1/files/" + id).header("Authorization", alice))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.extractionStatus").value("DONE"));
        // A second sweep has nothing left to do.
        assertThat(extraction.processPending()).isZero();
    }

    // ------------------------------------------------------------------ helpers

    private String upload(String name, byte[] content) throws Exception {
        String body =
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
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body).get("id").asString();
    }

    private String extractionStatus(String id) {
        return jdbc.queryForObject(
                "select extraction_status from archived_files where id = ?::uuid",
                String.class,
                id);
    }

    private List<String> search(String q) throws Exception {
        JsonNode page =
                json.readTree(
                        mockMvc.perform(get("/api/v1/files?q=" + q).header("Authorization", alice))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        List<String> ids = new ArrayList<>();
        page.get("content").forEach(f -> ids.add(f.get("id").asString()));
        return ids;
    }

    private static byte[] pdf(String text) throws Exception {
        try (PDDocument doc = new PDDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 700);
                stream.showText(text);
                stream.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] docx(String text) throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText(text);
            doc.write(out);
            return out.toByteArray();
        }
    }
}
