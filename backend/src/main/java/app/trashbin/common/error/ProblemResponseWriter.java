package app.trashbin.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Writes problem+json for errors raised inside the security filter chain, where the MVC exception
 * advice does not apply, so 401/403 look exactly like every other API error. The triggering
 * exception is deliberately not echoed.
 */
@Component
public class ProblemResponseWriter {

    private final ObjectMapper objectMapper;

    public ProblemResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) -> write(response, unauthenticated());
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> write(response, forbidden());
    }

    static ProblemDetail unauthenticated() {
        return Problems.of(ErrorCode.UNAUTHENTICATED, "Authentication is required.");
    }

    static ProblemDetail forbidden() {
        return Problems.of(ErrorCode.FORBIDDEN, "You are not allowed to do this.");
    }

    public void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
