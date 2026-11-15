package app.trashbin.auth.service;

import app.trashbin.config.AuthProperties;
import app.trashbin.config.JwtConfig;
import app.trashbin.user.dto.UserProfile;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues short-lived, signed access tokens (docs/SECURITY_GUIDELINES.md 3.2). */
@Service
public class AccessTokenService {

    public record AccessToken(String value, long expiresInSeconds) {}

    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final Clock clock;

    public AccessTokenService(JwtEncoder encoder, AuthProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public AccessToken issue(UserProfile user) {
        Instant now = Instant.now(clock);
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(JwtConfig.ISSUER)
                        .audience(List.of(JwtConfig.AUDIENCE))
                        .subject(user.id().toString())
                        .id(UUID.randomUUID().toString())
                        .issuedAt(now)
                        .expiresAt(now.plus(properties.accessTokenTtl()))
                        .claim("roles", List.of(user.role()))
                        .build();
        String value =
                encoder.encode(
                                JwtEncoderParameters.from(
                                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                        .getTokenValue();
        return new AccessToken(value, properties.accessTokenTtl().toSeconds());
    }
}
