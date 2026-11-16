package app.trashbin.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Small helpers that keep integration tests focused on behavior rather than request plumbing. */
public record ApiClient(MockMvc mockMvc, ObjectMapper json) {

    /** Uploads a text file with optional metadata and returns the created file's id. */
    public String upload(String bearer, String name, String content, Map<String, ?> metadata)
            throws Exception {
        var request =
                multipart("/api/v1/files")
                        .file(
                                new MockMultipartFile(
                                        "file",
                                        name,
                                        "text/plain",
                                        content.getBytes(StandardCharsets.UTF_8)));
        if (metadata != null) {
            request.file(
                    new MockMultipartFile(
                            "metadata", "", "application/json", json.writeValueAsBytes(metadata)));
        }
        return read(mockMvc.perform(request.header("Authorization", bearer))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asString();
    }

    public String createCollection(String bearer, String name) throws Exception {
        return read(mockMvc.perform(
                                post("/api/v1/collections")
                                        .header("Authorization", bearer)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsString(Map.of("name", name))))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asString();
    }

    public void putJson(String bearer, String url, Map<String, ?> body) throws Exception {
        mockMvc.perform(
                        put(url).header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    public JsonNode read(String body) throws Exception {
        return json.readTree(body);
    }
}
