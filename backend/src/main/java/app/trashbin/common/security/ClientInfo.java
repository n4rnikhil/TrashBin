package app.trashbin.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/** Informational details about the calling client, recorded with sessions. Never trusted. */
public record ClientInfo(String ipAddress, String userAgent) {

    private static final int MAX_USER_AGENT = 255;

    public static ClientInfo from(HttpServletRequest request) {
        String agent = request.getHeader(HttpHeaders.USER_AGENT);
        if (agent != null && agent.length() > MAX_USER_AGENT) {
            agent = agent.substring(0, MAX_USER_AGENT);
        }
        return new ClientInfo(request.getRemoteAddr(), agent);
    }
}
