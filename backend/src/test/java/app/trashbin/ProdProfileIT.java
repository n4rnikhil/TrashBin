package app.trashbin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.trashbin.support.TestData;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * Release smoke test for the {@code prod} profile (SECURITY_GUIDELINES §12): no API docs, actuator
 * limited to health even for admins, Secure refresh cookie, HSTS on HTTPS only. {@code prod} is
 * listed last so it overrides the test profile, exactly as it would override defaults.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "prod"})
class ProdProfileIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        TestData.resetDatabase(jdbc);
    }

    @Test
    void apiDocsAndSwaggerAreUnreachable() throws Exception {
        for (String path :
                new String[] {"/v3/api-docs", "/swagger-ui.html", "/swagger-ui/index.html"}) {
            int code = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
            assertThat(code).as(path).isEqualTo(404);
        }
    }

    @Test
    void actuatorExposesOnlyHealthEvenToAnAdmin() throws Exception {
        String admin = TestData.bearerFor(mockMvc, json, "admin@example.com");
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        for (String path : new String[] {"/actuator/env", "/actuator/beans", "/actuator/info"}) {
            int code =
                    mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, admin))
                            .andReturn()
                            .getResponse()
                            .getStatus();
            assertThat(code).as(path).isEqualTo(404);
        }
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshCookieIsSecure() throws Exception {
        TestData.bearerFor(mockMvc, json, "secure@example.com");
        String cookie =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "email",
                                                                "secure@example.com",
                                                                "password",
                                                                TestData.PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie).contains("tb_refresh=", "Secure", "HttpOnly", "SameSite=Strict");
    }

    @Test
    void hstsIsSentOverHttpsOnly() throws Exception {
        mockMvc.perform(get("/api/v1/health").secure(true))
                .andExpect(
                        header().string(
                                        "Strict-Transport-Security",
                                        "max-age=31536000 ; includeSubDomains"));
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }
}
