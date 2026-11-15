package app.trashbin.config;

import app.trashbin.common.security.Secrets;
import java.util.Arrays;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The access-ticket signing key, kept separate from the access-token key. */
@Configuration(proxyBeanMethods = false)
public class ContentConfig {

    /** Wrapper so the ticket key can never be injected where the JWT key is expected. */
    public record TicketSigningKey(SecretKey key) {}

    @Bean
    public TicketSigningKey ticketSigningKey(ContentProperties content, AuthProperties auth) {
        byte[] ticketKey = Secrets.decodeKey(content.ticketSecret(), "TRASHBIN_TICKET_SECRET");
        byte[] jwtKey = Secrets.decodeKey(auth.jwtSecret(), "TRASHBIN_JWT_SECRET");
        if (Arrays.equals(ticketKey, jwtKey)) {
            throw new IllegalStateException(
                    "TRASHBIN_TICKET_SECRET must differ from TRASHBIN_JWT_SECRET");
        }
        return new TicketSigningKey(new SecretKeySpec(ticketKey, "HmacSHA256"));
    }
}
