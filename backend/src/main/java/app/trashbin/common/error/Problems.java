package app.trashbin.common.error;

import app.trashbin.common.logging.TraceIdFilter;
import java.net.URI;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

/** Builds RFC 9457 problem documents in the one shape the API promises. */
public final class Problems {

    private static final String TYPE_BASE = "https://trashbin.local/problems/";

    private Problems() {}

    public static ProblemDetail of(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setTitle(code.title());
        return enrich(problem, code);
    }

    /** Adds the stable {@code code}, problem {@code type} and the request's {@code traceId}. */
    public static ProblemDetail enrich(ProblemDetail problem, ErrorCode code) {
        problem.setType(URI.create(TYPE_BASE + code.slug()));
        problem.setProperty("code", code.name());
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        return problem;
    }
}
