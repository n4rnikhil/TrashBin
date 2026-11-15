package app.trashbin.auth.web;

import app.trashbin.auth.service.FixedWindowRateLimiter;
import app.trashbin.common.error.ErrorCode;
import app.trashbin.common.error.ProblemResponseWriter;
import app.trashbin.common.error.Problems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/** Throttles the unauthenticated auth endpoints per client address before any work is done. */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    private final FixedWindowRateLimiter limiter;
    private final ProblemResponseWriter problems;

    public AuthRateLimitFilter(FixedWindowRateLimiter limiter, ProblemResponseWriter problems) {
        this.limiter = limiter;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !request.getRequestURI().startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (limiter.tryAcquire(request.getRemoteAddr())) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Auth rate limit exceeded for a client address");
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(limiter.retryAfterSeconds()));
        problems.write(
                response,
                Problems.of(
                        ErrorCode.TOO_MANY_REQUESTS,
                        "Too many requests. Please try again shortly."));
    }
}
