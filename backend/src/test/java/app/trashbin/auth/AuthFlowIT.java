package app.trashbin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.trashbin.support.MutableClock;
import app.trashbin.support.TestClockConfig;
import app.trashbin.support.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** End-to-end authentication behavior against the real database (docs/SECURITY_GUIDELINES.md 3). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class AuthFlowIT {

    private static final String EMAIL = "sam@example.com";
    private static final String PASSWORD = "correct horse battery";
    private static final String COOKIE = "tb_refresh";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MutableClock clock;

    @BeforeEach
    void cleanDatabase() {
        TestData.resetDatabase(jdbc);
    }

    // ---------------------------------------------------------------- registration

    @Test
    void registerCreatesAUserAndNeverReturnsCredentials() throws Exception {
        register(EMAIL, PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
        assertThat(jdbc.queryForObject("select password_hash from users", String.class))
                .startsWith("{bcrypt}");
    }

    @Test
    void emailIsNormalizedAndDuplicatesAreRejected() throws Exception {
        register("  Sam@Example.COM ", PASSWORD).andExpect(status().isBadRequest());
        register("Sam@Example.COM", PASSWORD).andExpect(jsonPath("$.email").value(EMAIL));
        register(EMAIL, PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void bootstrapAdminEmailBecomesAdmin() throws Exception {
        register("admin@example.com", PASSWORD).andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void weakOrOverlongPasswordsAreRejectedWithFieldErrors() throws Exception {
        register(EMAIL, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
        // 30 characters but 90 bytes in UTF-8: passes @Size, must fail the 72-byte rule.
        register(EMAIL, "€".repeat(30))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ---------------------------------------------------------------- login

    @Test
    void loginIssuesAccessTokenAndHttpOnlyStrictCookie() throws Exception {
        register(EMAIL, PASSWORD);

        MvcResult result = login(EMAIL, PASSWORD).andExpect(status().isOk()).andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie)
                .contains("tb_refresh=")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Path=/api/v1/auth");
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("refreshToken")).isNull();
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void wrongPasswordAndUnknownEmailAreIndistinguishable() throws Exception {
        register(EMAIL, PASSWORD);

        String wrong =
                login(EMAIL, "wrong password!!").andReturn().getResponse().getContentAsString();
        String unknown =
                login("nobody@example.com", PASSWORD)
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(stripTrace(wrong)).isEqualTo(stripTrace(unknown));
        assertThat(json.readTree(wrong).get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void accountLocksAfterFiveFailuresAndUnlocksAfterTheLockDuration() throws Exception {
        register(EMAIL, PASSWORD);
        for (int i = 0; i < 5; i++) {
            login(EMAIL, "wrong password!!").andExpect(status().isUnauthorized());
        }

        // Locked: even the correct password fails, with the same generic error.
        login(EMAIL, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        clock.advance(Duration.ofMinutes(16));
        login(EMAIL, PASSWORD).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- refresh

    @Test
    void refreshRotatesTheTokenAndTheOldOneStopsWorking() throws Exception {
        Session session = signIn();

        MvcResult refreshed =
                refresh(session.refreshToken()).andExpect(status().isOk()).andReturn();
        String newToken = refreshed.getResponse().getCookie(COOKIE).getValue();

        assertThat(newToken).isNotEqualTo(session.refreshToken());
        refresh(newToken).andExpect(status().isOk());
    }

    @Test
    void replayWithinGraceIsRejectedButKeepsTheSession() throws Exception {
        Session session = signIn();
        String second =
                refresh(session.refreshToken())
                        .andReturn()
                        .getResponse()
                        .getCookie(COOKIE)
                        .getValue();

        refresh(session.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        refresh(second).andExpect(status().isOk());
    }

    @Test
    void replayAfterGraceRevokesTheWholeSession() throws Exception {
        Session session = signIn();
        String second =
                refresh(session.refreshToken())
                        .andReturn()
                        .getResponse()
                        .getCookie(COOKIE)
                        .getValue();
        clock.advance(Duration.ofSeconds(21));

        refresh(session.refreshToken()).andExpect(status().isUnauthorized());

        refresh(second)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void refreshTokenExpiresSlidingAndAbsolutely() throws Exception {
        Session session = signIn();
        clock.advance(Duration.ofDays(15));
        refresh(session.refreshToken()).andExpect(status().isUnauthorized());

        // A session kept alive by regular use still ends at the absolute 30-day cap.
        String token = signIn().refreshToken();
        for (int day = 0; day < 29; day += 10) {
            clock.advance(Duration.ofDays(10));
            MvcResult result = refresh(token).andReturn();
            if (result.getResponse().getStatus() != 200) {
                break;
            }
            token = result.getResponse().getCookie(COOKIE).getValue();
        }
        clock.advance(Duration.ofDays(2));
        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void cookieEndpointsRequireTheGuardHeaderAndAnAllowedOrigin() throws Exception {
        Session session = signIn();
        Cookie cookie = new Cookie(COOKIE, session.refreshToken());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .cookie(cookie)
                                .header("X-Requested-With", "trashbin")
                                .header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/logout").cookie(cookie))
                .andExpect(status().isForbidden());

        // The rejected attempts must not have consumed the token.
        refresh(session.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void refreshWithoutOrWithGarbageCookieIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").header("X-Requested-With", "trashbin"))
                .andExpect(status().isUnauthorized());
        refresh("not-a-real-token").andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- logout / sessions

    @Test
    void logoutRevokesTheSessionAndClearsTheCookie() throws Exception {
        Session session = signIn();

        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/auth/logout")
                                        .cookie(new Cookie(COOKIE, session.refreshToken()))
                                        .header("X-Requested-With", "trashbin"))
                        .andExpect(status().isNoContent())
                        .andReturn();

        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");
        refresh(session.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutAllRevokesEverySessionOfTheUser() throws Exception {
        Session first = signIn();
        Session second = login();

        mockMvc.perform(post("/api/v1/auth/logout-all").header("Authorization", first.bearer()))
                .andExpect(status().isNoContent());

        refresh(first.refreshToken()).andExpect(status().isUnauthorized());
        refresh(second.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void sessionsCanBeListedAndRevokedButNotAcrossUsers() throws Exception {
        Session mine = signIn();
        register("other@example.com", PASSWORD);
        Session other = session(login("other@example.com", PASSWORD).andReturn());

        MvcResult list =
                mockMvc.perform(
                                get("/api/v1/users/me/sessions")
                                        .header("Authorization", mine.bearer()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.length()").value(1))
                        .andReturn();
        String mySessionId =
                json.readTree(list.getResponse().getContentAsString()).get(0).get("id").asText();

        // Another user cannot revoke my session: it does not exist for them.
        mockMvc.perform(
                        delete("/api/v1/users/me/sessions/" + mySessionId)
                                .header("Authorization", other.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        refresh(mine.refreshToken()).andExpect(status().isOk());

        Session renewed = login();
        mockMvc.perform(
                        delete("/api/v1/users/me/sessions/" + mySessionId)
                                .header("Authorization", renewed.bearer()))
                .andExpect(status().isNoContent());
    }

    // ---------------------------------------------------------------- profile / password

    @Test
    void profileCanBeUpdated() throws Exception {
        Session session = signIn();

        mockMvc.perform(
                        patch("/api/v1/users/me")
                                .header("Authorization", session.bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("displayName", "  Sam R. "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Sam R."));
    }

    @Test
    void passwordChangeRequiresTheCurrentPasswordAndSignsOutEverywhere() throws Exception {
        Session session = signIn();
        String newPassword = "a brand new passphrase";

        changePassword(session, "wrong password!!", newPassword)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        changePassword(session, PASSWORD, newPassword).andExpect(status().isNoContent());

        refresh(session.refreshToken()).andExpect(status().isUnauthorized());
        login(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
        login(EMAIL, newPassword).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- access token

    @Test
    void protectedEndpointsRejectMissingExpiredAndTamperedTokens() throws Exception {
        Session session = signIn();

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", session.bearer() + "x"))
                .andExpect(status().isUnauthorized());

        clock.advance(Duration.ofMinutes(11));
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", session.bearer()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actuatorBeyondHealthNeedsAdmin() throws Exception {
        Session user = signIn();

        mockMvc.perform(get("/actuator/env").header("Authorization", user.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ---------------------------------------------------------------- helpers

    private record Session(String accessToken, String refreshToken) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }

    private Session signIn() throws Exception {
        register(EMAIL, PASSWORD);
        return login();
    }

    private Session login() throws Exception {
        return session(login(EMAIL, PASSWORD).andExpect(status().isOk()).andReturn());
    }

    private Session session(MvcResult result) throws Exception {
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        return new Session(
                body.get("accessToken").asText(),
                result.getResponse().getCookie(COOKIE).getValue());
    }

    private String bearer(JsonNode body) {
        return "Bearer " + body.get("accessToken").asText();
    }

    private org.springframework.test.web.servlet.ResultActions register(
            String email, String password) throws Exception {
        return mockMvc.perform(
                jsonPost(
                        "/api/v1/auth/register",
                        Map.of("email", email, "displayName", "Sam", "password", password)));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password)
            throws Exception {
        return mockMvc.perform(
                jsonPost("/api/v1/auth/login", Map.of("email", email, "password", password)));
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String token)
            throws Exception {
        return mockMvc.perform(
                post("/api/v1/auth/refresh")
                        .cookie(new Cookie(COOKIE, token))
                        .header("X-Requested-With", "trashbin")
                        .header("Origin", "http://localhost:3000"));
    }

    private org.springframework.test.web.servlet.ResultActions changePassword(
            Session session, String current, String replacement) throws Exception {
        return mockMvc.perform(
                jsonPost(
                                "/api/v1/users/me/password",
                                Map.of("currentPassword", current, "newPassword", replacement))
                        .header("Authorization", session.bearer()));
    }

    private MockHttpServletRequestBuilder jsonPost(String url, Map<String, String> body)
            throws Exception {
        return post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
    }

    private String stripTrace(String problemJson) throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode node =
                (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(problemJson);
        node.remove("traceId");
        return node.toString();
    }
}
