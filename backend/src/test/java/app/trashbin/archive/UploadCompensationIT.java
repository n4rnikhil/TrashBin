package app.trashbin.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.trashbin.activity.service.ActivityService;
import app.trashbin.support.FileFixtures;
import app.trashbin.support.TestData;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** A database failure after the blob was stored must leave neither a row nor a blob behind. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UploadCompensationIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private ActivityService activity;

    @Value("${trashbin.storage.root}")
    private Path storageRoot;

    @Test
    void failedCommitRemovesTheStoredBlobAndHidesTheCause() throws Exception {
        TestData.resetDatabase(jdbc);
        FileFixtures.emptyDirectory(storageRoot.resolve("blobs"));
        String bearer = TestData.bearerFor(mockMvc, json, "carol@example.com");
        doThrow(new IllegalStateException("simulated database outage"))
                // Stub the spy behind the transactional proxy, not the proxy itself.
                .when(AopTestUtils.<ActivityService>getUltimateTargetObject(activity))
                .record(any(), any(), any(), any());

        mockMvc.perform(
                        multipart("/api/v1/files")
                                .file(
                                        new MockMultipartFile(
                                                "file",
                                                "a.txt",
                                                "text/plain",
                                                FileFixtures.text("will fail")))
                                .header("Authorization", bearer))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));

        assertThat(jdbc.queryForObject("select count(*) from archived_files", Long.class)).isZero();
        assertThat(FileFixtures.countFiles(storageRoot.resolve("blobs"))).isZero();
    }
}
