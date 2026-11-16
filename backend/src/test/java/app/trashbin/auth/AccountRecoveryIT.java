package app.trashbin.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.trashbin.auth.service.AuthService;
import app.trashbin.common.error.InvalidRequestException;
import app.trashbin.common.error.ResourceNotFoundException;
import app.trashbin.support.TestData;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/** Operator password recovery (ADR-003), as run by the {@code recovery} profile command. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountRecoveryIT {

    private static final String EMAIL = "forgot@example.com";
    private static final String NEW_PASSWORD = "a brand new passphrase";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AuthService auth;

    @BeforeEach
    void clean() throws Exception {
        TestData.resetDatabase(jdbc);
        TestData.bearerFor(mockMvc, json, EMAIL);
    }

    @Test
    void recoverySetsThePasswordLiftsTheLockoutAndEndsEverySession() throws Exception {
        Cookie session =
                login(TestData.PASSWORD)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getCookie("tb_refresh");
        for (int i = 0; i < 5; i++) {
            login("not the password").andExpect(status().isUnauthorized());
        }
        login(TestData.PASSWORD).andExpect(status().isUnauthorized()); // locked out

        auth.recoverAccount("  Forgot@Example.com ", NEW_PASSWORD);

        login(TestData.PASSWORD).andExpect(status().isUnauthorized());
        login(NEW_PASSWORD).andExpect(status().isOk());
        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .cookie(session)
                                .header("X-Requested-With", "trashbin")
                                .header("Origin", "http://localhost:3000"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownAccountsAndWeakPasswordsChangeNothing() throws Exception {
        assertThatThrownBy(() -> auth.recoverAccount("nobody@example.com", NEW_PASSWORD))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> auth.recoverAccount(EMAIL, "short"))
                .isInstanceOf(InvalidRequestException.class);
        login(TestData.PASSWORD).andExpect(status().isOk());
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                json.writeValueAsString(
                                        Map.of("email", EMAIL, "password", password))));
    }
}
