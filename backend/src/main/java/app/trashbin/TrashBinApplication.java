package app.trashbin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Trash Bin backend entry point.
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} is excluded on purpose: without it Spring Boot
 * creates a default in-memory user with a random password printed to the log. Real authentication
 * (JWT resource server) is introduced in milestone M1.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class TrashBinApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrashBinApplication.class, args);
    }
}
