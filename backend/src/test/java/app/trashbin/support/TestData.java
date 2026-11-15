package app.trashbin.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Shared helpers for integration tests: database reset and signed-in test accounts. */
public final class TestData {

    public static final String PASSWORD = "correct horse battery";

    private TestData() {}

    public static void resetDatabase(JdbcTemplate jdbc) {
        jdbc.execute(
                "TRUNCATE collection_files, collections, file_tags, tags, file_events, file_text_content, archived_files,"
                        + " refresh_tokens, users"
                        + " CASCADE");
    }

    /** Registers {@code email} and returns an {@code Authorization} header value for it. */
    public static String bearerFor(MockMvc mockMvc, ObjectMapper json, String email)
            throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "email", email,
                                                        "displayName", "Test User",
                                                        "password", PASSWORD))))
                .andExpect(status().isCreated());
        String body =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "email",
                                                                email,
                                                                "password",
                                                                PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return "Bearer " + json.readTree(body).get("accessToken").asText();
    }

    /** Signs in an existing test account again (e.g. after moving the test clock forward). */
    public static String login(MockMvc mockMvc, ObjectMapper json, String email) throws Exception {
        String body =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "email",
                                                                email,
                                                                "password",
                                                                PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return "Bearer " + json.readTree(body).get("accessToken").asText();
    }
}
