package app.trashbin.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Cryptographic beans: the access-token key, JWT encoder/decoder and the password encoder. */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

    public static final String ISSUER = "trashbin";
    public static final String AUDIENCE = "trashbin-api";
    private static final int MIN_KEY_BYTES = 32;

    /** Fails startup on a missing, malformed or weak secret instead of running insecurely. */
    @Bean
    SecretKey jwtSecretKey(AuthProperties properties) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(properties.jwtSecret().trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("TRASHBIN_JWT_SECRET must be Base64-encoded", e);
        }
        if (key.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "TRASHBIN_JWT_SECRET must decode to at least " + MIN_KEY_BYTES + " bytes");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey, Clock clock) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                        .macAlgorithm(MacAlgorithm.HS256)
                        .build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        timestamps,
                        new JwtIssuerValidator(ISSUER),
                        new JwtClaimValidator<List<String>>(
                                "aud", aud -> aud != null && aud.contains(AUDIENCE))));
        return decoder;
    }

    /** BCrypt behind a delegating encoder so the algorithm can be upgraded without a flag day. */
    @Bean
    PasswordEncoder passwordEncoder(AuthProperties properties) {
        return new DelegatingPasswordEncoder(
                "bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(properties.bcryptCost())));
    }
}
