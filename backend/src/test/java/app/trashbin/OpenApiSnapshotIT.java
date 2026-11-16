package app.trashbin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Contract guard (docs/API_CONTRACT.md section 6): the generated OpenAPI document must equal the
 * committed snapshot {@code backend/openapi/openapi.json}, which the frontend generates its types
 * from. After an intentional API change run the build with {@code UPDATE_OPENAPI_SNAPSHOT=true} and
 * commit the updated file.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiSnapshotIT {

    private static final Path SNAPSHOT = Path.of("openapi", "openapi.json");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void generatedSpecMatchesCommittedSnapshot() throws Exception {
        String body =
                mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode actual = objectMapper.readTree(body);

        if ("true".equals(System.getenv("UPDATE_OPENAPI_SNAPSHOT"))) {
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(
                    SNAPSHOT,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(actual)
                            + "\n");
        }

        assertThat(SNAPSHOT)
                .as("snapshot missing; run once with UPDATE_OPENAPI_SNAPSHOT=true")
                .exists();
        assertThat(actual).isEqualTo(objectMapper.readTree(Files.readString(SNAPSHOT)));
    }
}
