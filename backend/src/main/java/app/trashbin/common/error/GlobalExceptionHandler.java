package app.trashbin.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Single place where exceptions become HTTP responses (docs/CODING_STANDARDS.md B.5). Framework
 * exceptions keep Spring's status mapping and gain {@code code} and {@code traceId}. The {@link
 * Exception} handler is the one permitted catch-all: it logs the failure and returns a generic 500
 * that reveals nothing.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String GENERIC_DETAIL = "An unexpected error occurred.";

    @Override
    @Nullable
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ResponseEntity<Object> response =
                super.handleExceptionInternal(ex, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            ErrorCode code = codeFor(status);
            if (code != null) {
                Problems.enrich(problem, code);
            }
            if (status.is5xxServerError()) {
                problem.setDetail(GENERIC_DETAIL);
            }
        }
        return response;
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return toResponse(ProblemResponseWriter.unauthenticated());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return toResponse(ProblemResponseWriter.forbidden());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return toResponse(Problems.of(ErrorCode.INTERNAL_ERROR, GENERIC_DETAIL));
    }

    private static ResponseEntity<ProblemDetail> toResponse(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    @Nullable
    private static ErrorCode codeFor(HttpStatusCode status) {
        if (status.is5xxServerError()) {
            return ErrorCode.INTERNAL_ERROR;
        }
        return switch (status.value()) {
            case 400 -> ErrorCode.VALIDATION_FAILED;
            case 401 -> ErrorCode.UNAUTHENTICATED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            default -> null;
        };
    }
}
