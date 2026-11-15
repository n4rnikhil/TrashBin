package app.trashbin.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import app.trashbin.common.logging.TraceIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void unexpectedExceptionBecomesGenericInternalErrorWithoutLeakingDetails() {
        MDC.put(TraceIdFilter.MDC_KEY, "abc123");

        ResponseEntity<ProblemDetail> response =
                handler.handleUnexpected(new IllegalStateException("db password is hunter2"));

        ProblemDetail body = response.getBody();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body).isNotNull();
        assertThat(body.getDetail()).isEqualTo("An unexpected error occurred.");
        assertThat(body.getProperties())
                .containsEntry("code", "INTERNAL_ERROR")
                .containsEntry("traceId", "abc123");
        assertThat(body.toString()).doesNotContain("hunter2");
    }

    @Test
    void accessDeniedBecomesForbiddenProblemWithoutEchoingTheCause() {
        ResponseEntity<ProblemDetail> response =
                handler.handleAccessDenied(new AccessDeniedException("role check details"));

        ProblemDetail body = response.getBody();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(body).isNotNull();
        assertThat(body.getProperties()).containsEntry("code", "FORBIDDEN");
        assertThat(body.getDetail()).doesNotContain("role check");
    }
}
